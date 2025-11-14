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
import org.neo4j.driver.internal.InternalRelationship
import org.neo4j.driver.internal.value.NodeValue
import org.neo4j.driver.internal.value.RelationshipValue
import org.neo4j.driver.types.MapAccessor

/**
 * Unit tests for [toObject] extension function on [MapAccessor].
 * These tests verify that toObject works with any MapAccessor implementation,
 * including nodes, relationships, and records.
 */
class MapAccessorToObjectUnitTest {

    private fun node(vararg properties: Pair<String, Any?>): MapAccessor = InternalNode(
        1L,
        emptyList(),
        properties.toMap().mapValues { (_, value) -> Values.value(value) }
    )

    private fun relationship(vararg properties: Pair<String, Any?>): MapAccessor = InternalRelationship(
        1L,
        1L,
        2L,
        "TEST_TYPE",
        properties.toMap().mapValues { (_, value) -> Values.value(value) }
    )

    @Test
    fun `toObject should convert MapAccessor with primitive types to object`() {
        // given
        val mapAccessor = node(
            "name" to "Alice",
            "age" to 30
        )

        // when
        val person = mapAccessor.toObject<SimplePerson>()

        // then
        person should {
            have(name == "Alice")
            have(age == 30)
        }
    }

    @Test
    fun `toObject should work with Node MapAccessor`() {
        // given
        val node = node(
            "name" to "Bob",
            "age" to 25
        )

        // when
        val person = node.toObject<SimplePerson>()

        // then
        person should {
            have(name == "Bob")
            have(age == 25)
        }
    }

    @Test
    fun `toObject should work with Relationship MapAccessor`() {
        // given
        val relationship = relationship(
            "since" to 2020
        )

        // when
        val knows = relationship.toObject<KnowsSince>()

        // then
        knows should {
            have(since == 2020)
        }
    }

    @Test
    fun `toObject should handle nullable fields with null values`() {
        // given
        val mapAccessor = node(
            "name" to "Charlie",
            "age" to null,
            "email" to null
        )

        // when
        val person = mapAccessor.toObject<PersonWithNullables>()

        // then
        person should {
            have(name == "Charlie")
            have(age == null)
            have(email == null)
        }
    }

    @Test
    fun `toObject should handle nullable fields with non-null values`() {
        // given
        val mapAccessor = node(
            "name" to "David",
            "age" to 35,
            "email" to "david@example.com"
        )

        // when
        val person = mapAccessor.toObject<PersonWithNullables>()

        // then
        person should {
            have(name == "David")
            have(age == 35)
            have(email == "david@example.com")
        }
    }

    @Test
    fun `toObject should handle all primitive types`() {
        // given
        val mapAccessor = node(
            "name" to "Eve",
            "age" to 28,
            "height" to 165.5,
            "isActive" to true,
            "salary" to 85000L,
            "birthDate" to java.time.ZonedDateTime.parse("1995-03-20T00:00:00Z")
        )

        // when
        val person = mapAccessor.toObject<PersonWithAllTypes>()

        // then
        person should {
            have(name == "Eve")
            have(age == 28)
            have(height == 165.5)
            have(isActive)
            have(salary == 85000L)
            have(birthDate == Instant.parse("1995-03-20T00:00:00Z"))
        }
    }

    @Test
    fun `toObject should recursively convert nested maps to objects`() {
        // given
        val mapAccessor = node(
            "name" to "Frank",
            "age" to 42,
            "address" to mapOf(
                "street" to "456 Oak Ave",
                "city" to "Portland",
                "zipCode" to "97201"
            )
        )

        // when
        val person = mapAccessor.toObject<PersonWithAddress>()

        // then
        person should {
            have(name == "Frank")
            have(age == 42)
            address should {
                have(street == "456 Oak Ave")
                have(city == "Portland")
                have(zipCode == "97201")
            }
        }
    }

    @Test
    fun `toObject should throw when required nested object is missing`() {
        // given
        val mapAccessor = node(
            "name" to "Grace",
            "age" to 29
            // address is missing but required
        )

        // when/then
        assertThrows<SerializationException> {
            mapAccessor.toObject<PersonWithAddress>()
        }
    }

    @Test
    fun `toObject should handle null nested objects when optional`() {
        // given
        val mapAccessor = node(
            "name" to "Hank",
            "age" to 33,
            "address" to null
        )

        // when
        val person = mapAccessor.toObject<PersonWithOptionalAddress>()

        // then
        person should {
            have(name == "Hank")
            have(age == 33)
            have(address == null)
        }
    }

    @Test
    fun `toObject should handle missing optional nested objects gracefully`() {
        // given
        val mapAccessor = node(
            "name" to "Ivy",
            "age" to 27
            // address is missing and optional - should be set to null
        )

        // when
        val person = mapAccessor.toObject<PersonWithOptionalAddress>()

        // then
        person should {
            have(name == "Ivy")
            have(age == 27)
            have(address == null)
        }
    }

    @Test
    fun `toObject should convert lists to objects`() {
        // given
        val mapAccessor = node(
            "name" to "Jack",
            "tags" to listOf("developer", "architect", "mentor")
        )

        // when
        val person = mapAccessor.toObject<PersonWithList>()

        // then
        person should {
            have(name == "Jack")
            have(tags == listOf("developer", "architect", "mentor"))
        }
    }

    @Test
    fun `toObject should convert lists to sets`() {
        // given
        val mapAccessor = node(
            "name" to "Kate",
            "skills" to listOf("Rust", "Go", "TypeScript")
        )

        // when
        val person = mapAccessor.toObject<PersonWithSet>()

        // then
        person should {
            have(name == "Kate")
            have(skills == setOf("Rust", "Go", "TypeScript"))
        }
    }

    @Test
    fun `toObject should handle lists of numbers`() {
        // given
        val mapAccessor = node(
            "name" to "Leo",
            "scores" to listOf(88, 92, 85, 90)
        )

        // when
        val person = mapAccessor.toObject<PersonWithNumbers>()

        // then
        person should {
            have(name == "Leo")
            have(scores == listOf(88, 92, 85, 90))
        }
    }

    @Test
    fun `toObject should convert string to enum`() {
        // given
        val mapAccessor = node(
            "name" to "Maya",
            "role" to "USER"
        )

        // when
        val person = mapAccessor.toObject<PersonWithEnum>()

        // then
        person should {
            have(name == "Maya")
            have(role == Role.USER)
        }
    }

    @Test
    fun `toObject should throw on invalid enum value`() {
        // given
        val mapAccessor = node(
            "name" to "Nina",
            "role" to "SUPERUSER"
        )

        // when/then
        assertThrows<SerializationException> {
            mapAccessor.toObject<PersonWithEnum>()
        }
    }

    @Test
    fun `toObject should handle empty lists`() {
        // given
        val mapAccessor = node(
            "name" to "Oscar",
            "tags" to emptyList<String>()
        )

        // when
        val person = mapAccessor.toObject<PersonWithList>()

        // then
        person should {
            have(name == "Oscar")
            have(tags.isEmpty())
        }
    }

    @Test
    fun `toObject should throw when required field is missing`() {
        // given
        val mapAccessor = node(
            "name" to "Paul"
            // age is missing but required
        )

        // when/then
        assertThrows<SerializationException> {
            mapAccessor.toObject<SimplePerson>()
        }
    }

    @Test
    fun `toObject should throw when field type is wrong`() {
        // given
        val mapAccessor = node(
            "name" to "Quinn",
            "age" to "twenty-five" // Wrong type
        )

        // when/then
        assertThrows<Uncoercible> {
            mapAccessor.toObject<SimplePerson>()
        }
    }

    @Test
    fun `toObject should handle numeric type conversions`() {
        // given - Neo4j may return different numeric types
        val mapAccessor = node(
            "name" to "Rita",
            "age" to 40L // Long instead of Int
        )

        // when
        val person = mapAccessor.toObject<SimplePerson>()

        // then
        person should {
            have(name == "Rita")
            have(age == 40)
        }
    }

    @Test
    fun `toObject should handle complex relationship properties from MapAccessor`() {
        // given
        val relationship = relationship(
            "position" to "Senior Engineer",
            "since" to 2018,
            "salary" to 120000L
        )

        // when
        val worksFor = relationship.toObject<WorksFor>()

        // then
        worksFor should {
            have(position == "Senior Engineer")
            have(since == 2018)
            have(salary == 120000L)
        }
    }

    // Tests for Value wrapper handling (conditional logic in MapAccessor.toObject())

    @Test
    fun `toObject should extract Node from NodeValue wrapper`() {
        // given - Create a NodeValue wrapper
        val nodeValue = NodeValue(
            InternalNode(
                1L,
                emptyList(),
                mapOf(
                    "name" to Values.value("Steve"),
                    "age" to Values.value(45)
                )
            )
        )

        // when - Call toObject on the NodeValue (MapAccessor)
        val person = nodeValue.toObject<SimplePerson>()

        // then - Should successfully extract and convert
        person should {
            have(name == "Steve")
            have(age == 45)
        }
    }

    @Test
    fun `toObject should extract Relationship from RelationshipValue wrapper`() {
        // given - Create a RelationshipValue wrapper
        val relationshipValue = RelationshipValue(
            InternalRelationship(
                1L,
                1L,
                2L,
                "KNOWS",
                mapOf(
                    "since" to Values.value(2015)
                )
            )
        )

        // when - Call toObject on the RelationshipValue (MapAccessor)
        val knows = relationshipValue.toObject<KnowsSince>()

        // then - Should successfully extract and convert
        knows should {
            have(since == 2015)
        }
    }

    @Test
    fun `toObject should throw IllegalArgumentException for non-entity Value types`() {
        // given - A Value that is not a Node or Relationship (e.g., a String value)
        val stringValue = Values.value("just a string")

        // when/then - Should throw IllegalArgumentException with helpful message
        val exception = assertThrows<IllegalArgumentException> {
            stringValue.toObject<SimplePerson>()
        }

        // then - Verify the exact error message
        exception.message should {
            have(
                this == "Value must be a Node or Relationship to convert to object, but was STRING. " +
                        "Ensure your Cypher query returns nodes/relationships (e.g., 'RETURN n' not 'RETURN n.name')."
            )
        }
    }

    @Test
    fun `toObject should throw IllegalArgumentException for integer Value`() {
        // given - A Value that is a primitive (integer)
        val intValue = Values.value(42)

        // when/then - Should throw IllegalArgumentException
        val exception = assertThrows<IllegalArgumentException> {
            intValue.toObject<SimplePerson>()
        }

        // then - Verify the exact error message
        exception.message should {
            have(
                this == "Value must be a Node or Relationship to convert to object, but was INTEGER. " +
                        "Ensure your Cypher query returns nodes/relationships (e.g., 'RETURN n' not 'RETURN n.name')."
            )
        }
    }

    @Test
    fun `toObject should throw IllegalArgumentException for list Value`() {
        // given - A Value that is a list
        val listValue = Values.value(listOf("a", "b", "c"))

        // when/then - Should throw IllegalArgumentException
        val exception = assertThrows<IllegalArgumentException> {
            listValue.toObject<PersonWithList>()
        }

        // then - Verify the exact error message
        exception.message should {
            have(
                this == "Value must be a Node or Relationship to convert to object, but was LIST OF ANY?. " +
                        "Ensure your Cypher query returns nodes/relationships (e.g., 'RETURN n' not 'RETURN n.name')."
            )
        }
    }

}
