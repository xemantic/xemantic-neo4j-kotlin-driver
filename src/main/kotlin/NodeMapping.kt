/*
 * Copyright 2025 Kazimierz Pogoda / Xemantic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xemantic.neo4j.driver

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.neo4j.driver.types.MapAccessor
import org.neo4j.driver.types.Node
import org.neo4j.driver.types.Relationship
import org.neo4j.driver.Value
import org.neo4j.driver.exceptions.value.ValueException
import org.neo4j.driver.internal.value.NodeValue
import org.neo4j.driver.internal.value.RelationshipValue
import kotlin.time.toJavaInstant

/**
 * Converts a [kotlinx.serialization.Serializable] Kotlin object to a map of Neo4j node properties.
 *
 * This function converts objects to Neo4j-compatible property maps with the following rules:
 * - Primitive types (String, Int, Long, Double, Boolean) are preserved
 * - Lists and Sets of primitives are converted to lists
 * - Enums are converted to their string representation
 * - Null values are preserved
 *
 * **Important limitations:**
 * - Nested objects are **NOT** supported (will throw [ValueException])
 * - Maps are **NOT** supported (will throw [ValueException])
 * - Lists/Sets of objects are **NOT** supported (will throw [ValueException])
 *
 * These limitations exist because Neo4j properties can only store primitive types and
 * arrays of primitives. For complex data structures, use separate nodes connected by relationships.
 *
 * Sample usage:
 *
 * ```
 * @Serializable
 * data class Person(val name: String, val age: Int)
 *
 * val person = Person("Alice", 30)
 * val properties = person.toProperties()
 * // properties = mapOf("name" to "Alice", "age" to 30)
 *
 * neo4j.write { tx ->
 *     tx.run(
 *         query = $$"CREATE (p:Person $props) RETURN p",
 *         parameters = mapOf("props" to properties)
 *     )
 * }
 * ```
 *
 * @param T the type of the serializable object
 * @return a map of property names to values suitable for Neo4j
 * @throws ValueException if the object contains nested structures
 *
 * @see toObject
 */
public inline fun <reified T> T.toProperties(): Map<String, Any?> {
    val serializer = serializer<T>()
    val encoder = MapEncoder()
    encoder.encodeSerializableValue(serializer, this)
    @Suppress("UNCHECKED_CAST")
    return encoder.result as? Map<String, Any?> ?: emptyMap()
}

/**
 * Converts a Neo4j [Value] containing a [Node] or [Relationship] to a [kotlinx.serialization.Serializable] Kotlin object.
 *
 * This is a convenience function that simplifies the common pattern of extracting an entity from a Value
 * and converting it to an object. Instead of chaining `.asNode().toObject<T>()` or `.asRelationship().toObject<T>()`,
 * you can directly call `.toObject<T>()` on the Value.
 *
 * Sample usage:
 *
 * ```
 * @Serializable
 * data class Person(val name: String, val age: Int)
 *
 * @Serializable
 * data class Knows(val since: Int)
 *
 * neo4j.read { tx ->
 *     // Simplified API - no need for .asNode()
 *     val result = tx.run("MATCH (p:Person) RETURN p")
 *     result.records().collect { record ->
 *         val person = record["p"].toObject<Person>()
 *         println(person) // Person(name="Alice", age=30)
 *     }
 *
 *     // Works for relationships too - no need for .asRelationship()
 *     val relResult = tx.run("MATCH ()-[r:KNOWS]->() RETURN r")
 *     relResult.records().collect { record ->
 *         val knows = record["r"].toObject<Knows>()
 *         println(knows) // Knows(since=2020)
 *     }
 * }
 * ```
 *
 * @param T the type to deserialize to (must be [kotlinx.serialization.Serializable])
 * @return the deserialized object
 * @throws IllegalArgumentException if the Value is not a Node or Relationship
 * @throws kotlinx.serialization.SerializationException if deserialization fails
 *
 * @see toProperties
 */
public inline fun <reified T> Value.toObject(): T = when (this) {
    is NodeValue -> (asNode() as MapAccessor).toObject()
    is RelationshipValue -> (asRelationship() as MapAccessor).toObject()
    else -> throw IllegalArgumentException(
        "Value must be a Node or Relationship to convert to object, but was ${type().name()}. " +
        "Use .asNode() for nodes or .asRelationship() for relationships before calling toObject(), " +
        "or ensure your Cypher query returns nodes/relationships (e.g., 'RETURN n' not 'RETURN n.name')."
    )
}

/**
 * Internal function to convert a MapAccessor to a serializable object.
 */
@PublishedApi
internal inline fun <reified T> MapAccessor.toObject(): T {
    val serializer = serializer<T>()
    val decoder = MapAccessorDecoder(this)
    return decoder.decodeSerializableValue(serializer)
}

/**
 * Encoder that converts serializable objects to Maps for Neo4j.
 */
@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal class MapEncoder : AbstractEncoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    var result: Any? = null
    private val stack = ArrayDeque<Any>()
    private val savedKeys = ArrayDeque<String?>()
    private var currentElementDescriptor: SerialDescriptor? = null

    override fun encodeNull() {
        addValue(null)
    }

    override fun encodeBoolean(value: Boolean) {
        addValue(value)
    }

    override fun encodeByte(value: Byte) {
        addValue(value.toLong())
    }

    override fun encodeShort(value: Short) {
        addValue(value.toLong())
    }

    override fun encodeInt(value: Int) {
        addValue(value)
    }

    override fun encodeLong(value: Long) {
        addValue(value)
    }

    override fun encodeFloat(value: Float) {
        addValue(value.toDouble())
    }

    override fun encodeDouble(value: Double) {
        addValue(value)
    }

    override fun encodeChar(value: Char) {
        addValue(value.toString())
    }

    override fun encodeString(value: String) {
        // Check if this is a kotlin.time.Instant being serialized as a string
        // using the descriptor to avoid heuristic string parsing
        val isInstant = currentElementDescriptor?.serialName == "kotlin.time.Instant"

        if (isInstant) {
            // Parse the ISO 8601 string and convert to java.time.ZonedDateTime for Neo4j
            val instant = kotlin.time.Instant.parse(value)
            val javaInstant = instant.toJavaInstant()
            val zonedDateTime = java.time.ZonedDateTime.ofInstant(javaInstant, java.time.ZoneOffset.UTC)
            addValue(zonedDateTime)
        } else {
            addValue(value)
        }
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        addValue(enumDescriptor.getElementName(index))
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        // Save current state before entering nested structure
        savedKeys.addLast(currentKey)

        when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> {
                val map = mutableMapOf<String, Any?>()
                stack.addLast(map)
            }
            StructureKind.LIST -> {
                val list = mutableListOf<Any?>()
                stack.addLast(list)
            }
            StructureKind.MAP -> {
                // Maps are not supported in Neo4j properties
                if (stack.isEmpty()) {
                    // Top-level Map
                    throw ValueException(
                        "Maps are not supported as Neo4j properties. " +
                        "Use a data class with explicit properties instead."
                    )
                } else {
                    // Nested Map
                    throw ValueException(
                        "Nested maps are not supported in Neo4j properties. " +
                        "Use separate nodes connected by relationships instead."
                    )
                }
            }
            else -> {}
        }
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (stack.isNotEmpty()) {
            result = stack.removeLast()

            // Restore saved state
            val savedKey = if (savedKeys.isNotEmpty()) savedKeys.removeLast() else null

            if (stack.isNotEmpty()) {
                // Validate that we're not adding unsupported nested structures
                val parent = stack.last()
                when {
                    parent is MutableMap<*, *> && result is Map<*, *> -> {
                        // Nested object detected
                        throw ValueException(
                            "Nested objects are not supported in Neo4j properties. " +
                            "Property '${savedKey}' contains a nested object. " +
                            "Use separate nodes connected by relationships instead."
                        )
                    }
                    parent is MutableList<*> && result is Map<*, *> -> {
                        // List of objects detected
                        throw ValueException(
                            "Lists of objects are not supported in Neo4j properties. " +
                            "Use separate nodes connected by relationships instead."
                        )
                    }
                }

                when (parent) {
                    is MutableMap<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val map = parent as MutableMap<String, Any?>
                        savedKey?.let { map[it] = result }
                    }
                    is MutableList<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        (parent as MutableList<Any?>).add(result)
                    }
                }
            }
        }
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        if (stack.isNotEmpty() && stack.last() is MutableMap<*, *>) {
            currentKey = descriptor.getElementName(index)
            currentElementDescriptor = descriptor.getElementDescriptor(index)
        }
        return true
    }

    private var currentKey: String? = null

    private fun addValue(value: Any?) {
        when {
            stack.isEmpty() -> result = value
            stack.last() is MutableMap<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = stack.last() as MutableMap<String, Any?>
                currentKey?.let { map[it] = value }
            }
            stack.last() is MutableList<*> -> {
                @Suppress("UNCHECKED_CAST")
                (stack.last() as MutableList<Any?>).add(value)
            }
        }
    }

}

/**
 * Decoder that converts MapAccessor (Node/Relationship properties) to serializable objects.
 */
@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal class MapAccessorDecoder(
    private val mapAccessor: MapAccessor
) : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    private var elementIndex = 0
    private var currentKey: String? = null

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex >= descriptor.elementsCount) {
            return CompositeDecoder.DECODE_DONE
        }
        val name = descriptor.getElementName(elementIndex)
        currentKey = name
        return elementIndex++
    }

    override fun decodeNotNullMark(): Boolean {
        val key = currentKey ?: return false
        return mapAccessor.containsKey(key) && !mapAccessor[key].isNull
    }

    override fun decodeNull(): Nothing? = null

    override fun decodeBoolean(): Boolean = getCurrentValue().asBoolean()

    override fun decodeByte(): Byte = getCurrentValue().asLong().toByte()

    override fun decodeShort(): Short = getCurrentValue().asLong().toShort()

    override fun decodeInt() = getCurrentValue().asInt()

    override fun decodeLong() = getCurrentValue().asLong()

    override fun decodeFloat(): Float = getCurrentValue().asDouble().toFloat()

    override fun decodeDouble(): Double = getCurrentValue().asDouble()

    override fun decodeChar(): Char {
        val value = getCurrentValue()
        val str = value.asString()
        return str.firstOrNull() ?: throw SerializationException(
            "Expected Char but got empty string"
        )
    }

    override fun decodeString(): String {
        val value = getCurrentValue()
        // Handle DateTime values (for kotlin.time.Instant deserialization)
        return if (value.type().name() == "DATE_TIME") {
            value.asInstant().toString()
        } else {
            value.asString()
        }
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val value = decodeString()
        return (0 until enumDescriptor.elementsCount).firstOrNull {
            enumDescriptor.getElementName(it) == value
        } ?: throw SerializationException("Unknown enum value: $value")
    }

    private fun getCurrentValue(): Value {
        val key = currentKey ?: throw SerializationException("No current element")
        if (!mapAccessor.containsKey(key)) {
            throw SerializationException("Missing required field: $key")
        }
        return mapAccessor[key]
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        // For root-level decoding, currentKey will be null, so return this decoder
        if (currentKey == null) return this

        val value = getCurrentValue()

        return when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> {
                // Neo4j stores nested objects as Maps
                @Suppress("UNCHECKED_CAST")
                val nestedMap = value.asMap() as Map<String, Any?>
                ValueMapDecoder(nestedMap)
            }
            StructureKind.LIST -> {
                // Neo4j stores lists as arrays
                val list = value.asList()
                ValueListDecoder(list)
            }
            StructureKind.MAP -> {
                // Neo4j stores maps as Maps
                @Suppress("UNCHECKED_CAST")
                val nestedMap = value.asMap() as Map<String, Any?>
                ValueMapDecoder(nestedMap)
            }
            else -> this
        }
    }
}

/**
 * Decoder for nested maps within Neo4j values.
 */
@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal class ValueMapDecoder(
    private val map: Map<String, Any?>
) : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    private var elementIndex = 0
    private var currentKey: String? = null

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex >= descriptor.elementsCount) {
            return CompositeDecoder.DECODE_DONE
        }
        val name = descriptor.getElementName(elementIndex)
        currentKey = name
        return elementIndex++
    }

    override fun decodeNotNullMark(): Boolean {
        val key = currentKey ?: return false
        return map.containsKey(key) && map[key] != null
    }

    override fun decodeNull(): Nothing? = null

    override fun decodeBoolean(): Boolean = getCurrentValue() as? Boolean
        ?: throw SerializationException("Expected Boolean")

    override fun decodeByte(): Byte = (getCurrentValue() as? Number)?.toByte()
        ?: throw SerializationException("Expected Byte")

    override fun decodeShort(): Short = (getCurrentValue() as? Number)?.toShort()
        ?: throw SerializationException("Expected Short")

    override fun decodeInt(): Int = (getCurrentValue() as? Number)?.toInt()
        ?: throw SerializationException("Expected Int")

    override fun decodeLong(): Long = (getCurrentValue() as? Number)?.toLong()
        ?: throw SerializationException("Expected Long")

    override fun decodeFloat(): Float = (getCurrentValue() as? Number)?.toFloat()
        ?: throw SerializationException("Expected Float")

    override fun decodeDouble(): Double = (getCurrentValue() as? Number)?.toDouble()
        ?: throw SerializationException("Expected Double")

    override fun decodeChar(): Char = (getCurrentValue() as? String)?.firstOrNull()
        ?: throw SerializationException("Expected Char")

    override fun decodeString() = when (val value = getCurrentValue()) {
        is String -> value
        is kotlin.time.Instant -> value.toString()
        else -> throw SerializationException("Expected String")
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val value = decodeString()
        return (0 until enumDescriptor.elementsCount).firstOrNull {
            enumDescriptor.getElementName(it) == value
        } ?: throw SerializationException("Unknown enum value: $value")
    }

    private fun getCurrentValue(): Any? {
        val key = currentKey ?: throw SerializationException("No current element")
        if (!map.containsKey(key)) {
            throw SerializationException("Missing required field: $key")
        }
        return map[key]
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        // For root-level decoding, currentKey will be null, so return this decoder
        if (currentKey == null) return this

        return when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> {
                when (val value = getCurrentValue()) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        ValueMapDecoder(value as Map<String, Any?>)
                    }
                    null -> throw SerializationException("Expected object but got null")
                    else -> throw SerializationException("Expected Map but got ${value::class}")
                }
            }
            StructureKind.LIST -> {
                when (val value = getCurrentValue()) {
                    is List<*> -> ValueListDecoder(value)
                    null -> ValueListDecoder(emptyList<Any?>())
                    else -> throw SerializationException("Expected List but got ${value::class}")
                }
            }
            StructureKind.MAP -> {
                when (val value = getCurrentValue()) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        ValueMapDecoder(value as Map<String, Any?>)
                    }
                    null -> throw SerializationException("Expected map but got null")
                    else -> throw SerializationException("Expected Map but got ${value::class}")
                }
            }
            else -> this
        }
    }
}

/**
 * Decoder for lists/collections within Neo4j values.
 */
@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal class ValueListDecoder(
    private val list: List<*>
) : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    private var elementIndex = 0

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex >= list.size) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = list.size

    override fun decodeNotNullMark(): Boolean {
        return list.getOrNull(elementIndex) != null
    }

    override fun decodeNull(): Nothing? = null

    override fun decodeBoolean(): Boolean = getCurrentValue() as? Boolean
        ?: throw SerializationException("Expected Boolean")

    override fun decodeByte(): Byte = (getCurrentValue() as? Number)?.toByte()
        ?: throw SerializationException("Expected Byte")

    override fun decodeShort(): Short = (getCurrentValue() as? Number)?.toShort()
        ?: throw SerializationException("Expected Short")

    override fun decodeInt(): Int = (getCurrentValue() as? Number)?.toInt()
        ?: throw SerializationException("Expected Int")

    override fun decodeLong(): Long = (getCurrentValue() as? Number)?.toLong()
        ?: throw SerializationException("Expected Long")

    override fun decodeFloat(): Float = (getCurrentValue() as? Number)?.toFloat()
        ?: throw SerializationException("Expected Float")

    override fun decodeDouble(): Double = (getCurrentValue() as? Number)?.toDouble()
        ?: throw SerializationException("Expected Double")

    override fun decodeChar(): Char = (getCurrentValue() as? String)?.firstOrNull()
        ?: throw SerializationException("Expected Char")

    override fun decodeString(): String = when (val value = getCurrentValue()) {
        is String -> value
        is kotlin.time.Instant -> value.toString()
        else -> throw SerializationException("Expected String")
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val value = decodeString()
        return (0 until enumDescriptor.elementsCount).firstOrNull {
            enumDescriptor.getElementName(it) == value
        } ?: throw SerializationException("Unknown enum value: $value")
    }

    private fun getCurrentValue(): Any {
        return list.getOrNull(elementIndex - 1) ?: throw SerializationException("No more elements")
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> {
                when (val value = getCurrentValue()) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        ValueMapDecoder(value as Map<String, Any?>)
                    }
                    else -> throw SerializationException("Expected Map but got ${value::class}")
                }
            }
            StructureKind.LIST -> {
                when (val value = getCurrentValue()) {
                    is List<*> -> ValueListDecoder(value)
                    else -> throw SerializationException("Expected List but got ${value::class}")
                }
            }
            else -> this
        }
    }
}
