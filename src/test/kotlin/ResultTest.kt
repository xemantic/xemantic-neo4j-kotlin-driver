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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.neo4j.driver.Record
import org.neo4j.driver.Values
import org.neo4j.driver.exceptions.NoSuchRecordException
import org.neo4j.driver.internal.InternalRecord

/**
 * Unit tests for [Result].
 * These tests verify the behavior of extension functions like [singleOrNull]
 * and [single] with transformation without requiring a full Neo4j instance.
 *
 * The tests use MockK to mock the [Result] interface, making them fast and isolated.
 * Only [Record] instances are created using Neo4j's [org.neo4j.driver.internal.InternalRecord]
 * with [Values] utility for realistic data.
 */
class ResultTest {

    @Test
    fun `singleOrNull should return null when result is empty`() = runTest {
        // given
        val result = mockk<Result> {
            every { records() } returns flowOf()
        }

        // when
        val record = result.singleOrNull()

        // then
        assert(record == null)
    }

    @Test
    fun `singleOrNull should return the single record when result has exactly one record`() = runTest {
        // given
        val mockRecord = createMockRecord("name" to "Alice")
        val result = mockk<Result> {
            every { records() } returns flowOf(mockRecord)
        }

        // when
        val record = result.singleOrNull()

        // then
        assert(record == mockRecord)
        record should {
            have(get("name").asString() == "Alice")
        }
    }

    @Test
    fun `singleOrNull should throw NoSuchRecordException when result has multiple records`() = runTest {
        // given
        val mockRecord1 = createMockRecord("name" to "Alice")
        val mockRecord2 = createMockRecord("name" to "Bob")
        val result = mockk<Result> {
            every { records() } returns flowOf(mockRecord1, mockRecord2)
        }

        // when
        val exception = assertThrows<NoSuchRecordException> {
            result.singleOrNull()
        }

        // then
        exception should {
            have(message == "Expected at most 1 record but found at least 2")
        }
    }

    @Test
    fun `singleOrNull should throw NoSuchRecordException early on second record`() = runTest {
        // given
        // Create a result with many records to verify we don't consume all of them
        val records = (1..10).map { createMockRecord("id" to it) }
        val result = mockk<Result> {
            every { records() } returns flowOf(*records.toTypedArray())
        }

        // when
        val exception = assertThrows<NoSuchRecordException> {
            result.singleOrNull()
        }

        // then
        exception should {
            have(message == "Expected at most 1 record but found at least 2")
        }
    }

    @Test
    fun `single with transformation should transform the single record`() = runTest {
        // given
        val mockRecord = createMockRecord("name" to "Alice", "age" to 30)
        val result = mockk<Result> {
            coEvery { single() } returns mockRecord
        }

        // when
        val name = result.single { record ->
            record["name"].asString()
        }

        // then
        assert(name == "Alice")
    }

    @Test
    fun `single with transformation should support complex transformations`() = runTest {
        // given
        val mockRecord = createMockRecord("name" to "Alice", "age" to 30)
        val result = mockk<Result> {
            coEvery { single() } returns mockRecord
        }

        // when
        val person = result.single { record ->
            SimplePerson(
                name = record["name"].asString(),
                age = record["age"].asInt()
            )
        }

        // then
        person should {
            have(name == "Alice")
            have(age == 30)
        }
    }

    @Test
    fun `single with transformation should propagate exceptions from block`() = runTest {
        // given
        val mockRecord = createMockRecord("name" to "Alice")
        val result = mockk<Result> {
            coEvery { single() } returns mockRecord
        }

        // when
        val exception = assertThrows<IllegalStateException> {
            result.single {
                error("Transformation failed")
            }
        }

        // then
        exception should {
            have(message == "Transformation failed")
        }
    }

    @Test
    fun `single with transformation should propagate NoSuchRecordException`() = runTest {
        // given
        val result = mockk<Result> {
            coEvery { single() } throws NoSuchRecordException("Expected exactly one record")
        }

        // when
        val exception = assertThrows<NoSuchRecordException> {
            result.single { record ->
                record["name"].asString()
            }
        }

        // then
        exception should {
            have(message == "Expected exactly one record")
        }
    }

    // Helper function to create mock Record instances

    private fun createMockRecord(
        vararg pairs: Pair<String, Any>
    ): Record {
        val keys = pairs.map { it.first }
        val values = pairs.map { Values.value(it.second) }
        return InternalRecord(
            keys.toList(),
            values.toList()
        )
    }

}
