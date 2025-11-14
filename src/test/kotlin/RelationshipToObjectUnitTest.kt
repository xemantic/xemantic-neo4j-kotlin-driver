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
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.neo4j.driver.Values
import org.neo4j.driver.exceptions.value.Uncoercible
import org.neo4j.driver.internal.InternalRelationship

/**
 * Unit tests for [toObject] extension function.
 * These tests verify relationship-to-object conversion using [InternalRelationship]
 * without requiring a full Neo4j instance.
 */
class RelationshipToObjectUnitTest {

    private fun relationship(vararg properties: Pair<String, Any?>) = InternalRelationship(
        1L,
        1L,
        2L,
        "TEST_TYPE",
        properties.toMap().mapValues { (_, value) -> Values.value(value) }
    )

    @Test
    fun `toObject should convert relationship with primitive types to object`() {
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
    fun `toObject should handle complex relationship properties`() {
        // given
        val relationship = relationship(
            "position" to "Software Engineer",
            "since" to 2020,
            "salary" to 100000L
        )

        // when
        val worksFor = relationship.toObject<WorksFor>()

        // then
        worksFor should {
            have(position == "Software Engineer")
            have(since == 2020)
            have(salary == 100000L)
        }
    }

    @Test
    fun `toObject should handle nullable properties with null values`() {
        // given
        val relationship = relationship(
            "position" to "Intern",
            "since" to 2023,
            "salary" to null
        )

        // when
        val worksFor = relationship.toObject<WorksFor>()

        // then
        worksFor should {
            have(position == "Intern")
            have(since == 2023)
            have(salary == null)
        }
    }

    @Test
    fun `toObject should handle missing optional properties`() {
        // given
        val relationship = relationship(
            "position" to "Intern",
            "since" to 2023
            // salary is missing and optional
        )

        // when
        val worksFor = relationship.toObject<WorksFor>()

        // then
        worksFor should {
            have(position == "Intern")
            have(since == 2023)
            have(salary == null)
        }
    }

    @Test
    fun `toObject should throw when required field is missing`() {
        // given
        val relationship = relationship(
            "position" to "Engineer"
            // since is missing but required
        )

        // when/then
        assertThrows<SerializationException> {
            relationship.toObject<WorksFor>()
        }
    }

    @Test
    fun `toObject should throw when field type is wrong`() {
        // given
        val relationship = relationship(
            "since" to "not a number" // Wrong type
        )

        // when/then
        assertThrows<Uncoercible> {
            relationship.toObject<KnowsSince>()
        }
    }

    @Test
    fun `toObject should handle numeric type conversions`() {
        // given - Neo4j may return different numeric types
        val relationship = relationship(
            "since" to 2020L // Long instead of Int
        )

        // when
        val knows = relationship.toObject<KnowsSince>()

        // then
        knows should {
            have(since == 2020)
        }
    }

}