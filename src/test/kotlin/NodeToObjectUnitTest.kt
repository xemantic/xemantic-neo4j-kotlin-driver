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

import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
import kotlin.time.Instant
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.neo4j.driver.Values
import org.neo4j.driver.exceptions.value.Uncoercible
import org.neo4j.driver.internal.InternalNode

/**
 * Unit tests for [toObject] extension function.
 * These tests verify entity-to-object conversion using InternalNode and InternalRelationship
 * without requiring a full Neo4j instance.
 */
class NodeToObjectUnitTest {

    private fun node(vararg properties: Pair<String, Any?>) = InternalNode(
        1L,
        emptyList(),
        properties.toMap().mapValues { (_, value) -> Values.value(value) }
    )

    @Test
    fun `toObject should convert node with primitive types to object`() {
        // given
        val node = node(
            "name" to "Alice",
            "age" to 30
        )

        // when
        val person = node.toObject<SimplePerson>()

        // then
        person should {
            have(name == "Alice")
            have(age == 30)
        }
    }

    @Test
    fun `toObject should handle nullable fields with null values`() {
        // given
        val node = node(
            "name" to "Bob",
            "age" to null,
            "email" to null
        )

        // when
        val person = node.toObject<PersonWithNullables>()

        // then
        person should {
            have(name == "Bob")
            have(age == null)
            have(email == null)
        }
    }

    @Test
    fun `toObject should handle nullable fields with non-null values`() {
        // given
        val node = node(
            "name" to "Charlie",
            "age" to 25,
            "email" to "charlie@example.com"
        )

        // when
        val person = node.toObject<PersonWithNullables>()

        // then
        person should {
            have(name == "Charlie")
            have(age == 25)
            have(email == "charlie@example.com")
        }
    }

    @Test
    fun `toObject should handle all primitive types`() {
        // given
        val node = node(
            "name" to "David",
            "age" to 35,
            "height" to 180.5,
            "isActive" to true,
            "salary" to 75000L,
            "birthDate" to java.time.ZonedDateTime.parse("1990-05-15T00:00:00Z")
        )

        // when
        val person = node.toObject<PersonWithAllTypes>()

        // then
        person should {
            have(name == "David")
            have(age == 35)
            have(height == 180.5)
            have(isActive)
            have(salary == 75000L)
            have(birthDate == Instant.parse("1990-05-15T00:00:00Z"))
        }
    }

    @Test
    fun `toObject should recursively convert nested maps to objects`() {
        // given
        val node = node(
            "name" to "Eve",
            "age" to 28,
            "address" to mapOf(
                "street" to "123 Main St",
                "city" to "Springfield",
                "zipCode" to "12345"
            )
        )

        // when
        val person = node.toObject<PersonWithAddress>()

        // then
        person should {
            have(name == "Eve")
            have(age == 28)
            address should {
                have(street == "123 Main St")
                have(city == "Springfield")
                have(zipCode == "12345")
            }
        }
    }

    @Test
    fun `toObject should throw when required nested object is missing`() {
        // given
        val node = node(
            "name" to "Frank",
            "age" to 40
            // address is missing but required
        )

        // when/then
        assertThrows<SerializationException> {
            node.toObject<PersonWithAddress>()
        }
    }

    @Test
    fun `toObject should handle null nested objects when optional`() {
        // given
        val node = node(
            "name" to "Frank",
            "age" to 40,
            "address" to null
        )

        // when
        val person = node.toObject<PersonWithOptionalAddress>()

        // then
        person should {
            have(name == "Frank")
            have(age == 40)
            have(address == null)
        }
    }

    @Test
    fun `toObject should handle missing optional nested objects gracefully`() {
        // given
        val node = node(
            "name" to "Frank",
            "age" to 40
            // address is missing and optional - should be set to null
        )

        // when
        val person = node.toObject<PersonWithOptionalAddress>()

        // then
        person should {
            have(name == "Frank")
            have(age == 40)
            have(address == null)
        }
    }

    @Test
    fun `toObject should convert lists to objects`() {
        // given
        val node = node(
            "name" to "Grace",
            "tags" to listOf("developer", "architect", "speaker")
        )

        // when
        val person = node.toObject<PersonWithList>()

        // then
        person should {
            have(name == "Grace")
            have(tags == listOf("developer", "architect", "speaker"))
        }
    }

    @Test
    fun `toObject should convert lists to sets`() {
        // given
        val node = node(
            "name" to "Hank",
            "skills" to listOf("Kotlin", "Java", "Python")
        )

        // when
        val person = node.toObject<PersonWithSet>()

        // then
        person should {
            have(name == "Hank")
            have(skills == setOf("Kotlin", "Java", "Python"))
        }
    }

    @Test
    fun `toObject should handle lists of numbers`() {
        // given
        val node = node(
            "name" to "Ivy",
            "scores" to listOf(95, 87, 92, 88)
        )

        // when
        val person = node.toObject<PersonWithNumbers>()

        // then
        person should {
            have(name == "Ivy")
            have(scores == listOf(95, 87, 92, 88))
        }
    }

    @Test
    fun `toObject should convert string to enum`() {
        // given
        val node = node(
            "name" to "Jack",
            "role" to "ADMIN"
        )

        // when
        val person = node.toObject<PersonWithEnum>()

        // then
        person should {
            have(name == "Jack")
            have(role == Role.ADMIN)
        }
    }

    @Test
    fun `toObject should throw on invalid enum value`() {
        // given
        val node = node(
            "name" to "Jack",
            "role" to "INVALID_ROLE"
        )

        // when/then
        assertThrows<SerializationException> {
            node.toObject<PersonWithEnum>()
        }
    }

    @Test
    fun `toObject should handle empty lists`() {
        // given
        val node = node(
            "name" to "Leo",
            "tags" to emptyList<String>()
        )

        // when
        val person = node.toObject<PersonWithList>()

        // then
        person should {
            have(name == "Leo")
            have(tags.isEmpty())
        }
    }

    @Test
    fun `toObject should throw when required field is missing`() {
        // given
        val node = node(
            "name" to "Alice"
            // age is missing
        )

        // when/then
        assertThrows<SerializationException> {
            node.toObject<SimplePerson>()
        }
    }

    @Test
    fun `toObject should throw when field type is wrong`() {
        // given
        val node = node(
            "name" to "Alice",
            "age" to "not a number" // Wrong type
        )

        // when/then
        assertThrows<Uncoercible> {
            node.toObject<SimplePerson>()
        }
    }

    @Test
    fun `toObject should handle numeric type conversions`() {
        // given - Neo4j may return different numeric types
        val node = node(
            "name" to "Alice",
            "age" to 30L // Long instead of Int
        )

        // when
        val person = node.toObject<SimplePerson>()

        // then
        person should {
            have(name == "Alice")
            have(age == 30)
        }
    }

}
