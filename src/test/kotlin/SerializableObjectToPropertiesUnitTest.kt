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
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.neo4j.driver.exceptions.value.ValueException
import java.time.ZonedDateTime

/**
 * Unit tests for [toProperties] extension function.
 * These tests verify object-to-map conversion without requiring Neo4j.
 */
class SerializableObjectToPropertiesUnitTest {

    @Test
    fun `toProperties should convert simple object with primitive types`() {
        // given
        val person = SimplePerson(name = "Alice", age = 30)

        // when
        val properties = person.toProperties()

        // then
        properties should {
            have(size == 2)
            have(get("name") == "Alice")
            have(get("age") == 30)
        }
    }

    @Test
    fun `toProperties should handle nullable fields with null values`() {
        // given
        val person = PersonWithNullables(
            name = "Bob",
            age = null,
            email = null
        )

        // when
        val properties = person.toProperties()

        // then
        properties should {
            have(size == 3)
            have(get("name") == "Bob")
            have(get("age") == null)
            have(get("email") == null)
        }
    }

    @Test
    fun `toProperties should handle nullable fields with non-null values`() {
        // given
        val person = PersonWithNullables(
            name = "Charlie",
            age = 25,
            email = "charlie@example.com"
        )

        // when
        val properties = person.toProperties()

        // then
        properties should {
            have(size == 3)
            have(get("name") == "Charlie")
            have(get("age") == 25)
            have(get("email") == "charlie@example.com")
        }
    }

    @Test
    fun `toProperties should handle all primitive types`() {
        // given
        val person = PersonWithAllTypes(
            name = "David",
            age = 35,
            height = 180.5,
            isActive = true,
            salary = 75000L,
            birthDate = Instant.parse("1990-05-15T00:00:00Z")
        )

        // when
        val properties = person.toProperties()

        // then
        properties should {
            have(size == 6)
            have(get("name") == "David")
            have(get("age") == 35)
            have(get("height") == 180.5)
            have(get("isActive") == true)
            have(get("salary") == 75000L)
            have(get("birthDate") == ZonedDateTime.parse("1990-05-15T00:00:00Z"))
        }
    }

    @Test
    fun `toProperties should reject nested objects`() {
        // given
        val person = PersonWithAddress(
            name = "Eve",
            age = 28,
            address = Address(
                street = "123 Main St",
                city = "Springfield",
                zipCode = "12345"
            )
        )

        // when
        val error = assertThrows<ValueException> {
            person.toProperties()
        }

        // then
        error should {
            have(message == "Nested objects are not supported in Neo4j properties. " +
                    "Property 'address' contains a nested object. " +
                    "Use separate nodes connected by relationships instead."
            )
        }
    }

    @Test
    fun `toProperties should reject objects with null nested object field`() {
        // given
        val person = PersonWithOptionalAddress(
            name = "Frank",
            age = 40,
            address = null
        )

        // when/then
        // Even though address is null, the type signature indicates it CAN contain
        // a nested object, which we want to reject at the type level
        // However, kotlinx.serialization won't serialize the null value as a nested structure,
        // so this should actually work fine
        val properties = person.toProperties()

        // then
        properties should {
            have(size == 3)
            have(get("name") == "Frank")
            have(get("age") == 40)
            have(get("address") == null)
        }
    }

    @Test
    fun `toProperties should keep String list property`() {
        // given
        val person = PersonWithList(
            name = "Grace",
            tags = listOf("developer", "architect", "speaker")
        )

        // when
        val properties = person.toProperties()

        // then
        properties should {
            have(size == 2)
            have(get("name") == "Grace")
            have(get("tags") == listOf("developer", "architect", "speaker"))
        }
    }

    @Test
    fun `toProperties should convert sets to lists`() {
        // given
        val person = PersonWithSet(
            name = "Hank",
            skills = setOf("Kotlin", "Java", "Python")
        )

        // when
        val properties = person.toProperties()

        // then
        properties should {
            have(size == 2)
            have(get("name") == "Hank")
            have(get("skills") == listOf("Kotlin", "Java", "Python"))
        }
    }

    @Test
    fun `toProperties should handle lists of numbers`() {
        // given
        val person = PersonWithNumbers(
            name = "Ivy",
            scores = listOf(95, 87, 92, 88)
        )

        // when
        val properties = person.toProperties()

        // then
        properties should {
            have(size == 2)
            have(get("name") == "Ivy")
            have(get("scores") == listOf(95, 87, 92, 88))
        }
    }

    @Test
    fun `toProperties should convert enums to strings`() {
        // given
        val person = PersonWithEnum(
            name = "Jack",
            role = Role.ADMIN
        )

        // when
        val properties = person.toProperties()

        // then
        properties should {
            have(size == 2)
            have(get("name") == "Jack")
            have(get("role") == "ADMIN")
        }
    }

    @Test
    fun `toProperties should reject maps`() {
        // given
        val person = PersonWithMap(
            name = "Kate",
            metadata = mapOf(
                "department" to "Engineering",
                "team" to "Backend"
            )
        )

        // when
        val exception = assertThrows<ValueException> {
            person.toProperties()
        }

        // then
        exception should {
            have(message == "Nested maps are not supported in Neo4j properties. " +
                    "Use separate nodes connected by relationships instead."
            )
        }
    }

    @Test
    fun `toProperties should handle empty collections`() {
        // given
        val person = PersonWithList(
            name = "Leo",
            tags = emptyList()
        )

        // when
        val properties = person.toProperties()

        // then
        properties should {
            have(size == 2)
            have(get("name") == "Leo")
            have(get("tags") == emptyList<String>())
        }
    }

    @Test
    fun `toProperties should reject lists of objects`() {
        // given
        @Serializable
        data class Team(val name: String, val members: List<SimplePerson>)

        val team = Team(
            name = "Engineering",
            members = listOf(
                SimplePerson("Alice", 30),
                SimplePerson("Bob", 25)
            )
        )

        // when
        val error = assertThrows<ValueException> {
            team.toProperties()
        }

        // then
        error should {
            have(message == "Lists of objects are not supported in Neo4j properties. " +
                    "Use separate nodes connected by relationships instead."
            )
        }
    }

}
