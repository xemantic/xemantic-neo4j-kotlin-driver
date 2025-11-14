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

import com.xemantic.kotlin.test.assert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Round-trip integration tests verifying that objects can be converted to
 * Neo4j node properties and back to objects without data loss.
 */
class NodeMappingRoundTripTest {

    private val neo4j = DispatchedNeo4jOperations(
        driver = TestNeo4j.driver,
        dispatcher = Dispatchers.IO.limitedParallelism(90)
    )

    @AfterEach
    fun cleanDatabase() {
        TestNeo4j.cleanDatabase()
    }

    @Test
    fun `should round-trip convert simple object through Neo4j`() = runTest {
        // given
        val original = SimplePerson(name = "Alice", age = 30)

        // when
        val retrieved = neo4j.write { tx ->
            tx.run(
                query = $$"CREATE (p:Person $props) RETURN p",
                parameters = mapOf("props" to original.toProperties())
            ).single()["p"].toObject<SimplePerson>()
        }

        // then
        assert(retrieved == original)
    }

    @Test
    fun `should round-trip convert object with collections`() = runTest {
        // given
        val original = PersonWithList(
            name = "Grace",
            tags = listOf("developer", "architect", "speaker")
        )

        // when
        val retrieved = neo4j.write { tx ->
            tx.run(
                query = $$"CREATE (p:Person $props) RETURN p",
                parameters = mapOf("props" to original.toProperties())
            ).single()["p"].toObject<PersonWithList>()
        }

        // then
        assert(retrieved == original)
    }

    @Test
    fun `should round-trip convert object with enum`() = runTest {
        // given
        val original = PersonWithEnum(
            name = "Jack",
            role = Role.ADMIN
        )

        // when
        val retrieved = neo4j.write { tx ->
            tx.run(
                query = $$"CREATE (p:Person $props) RETURN p",
                parameters = mapOf("props" to original.toProperties())
            ).single()["p"].toObject<PersonWithEnum>()
        }

        // then
        assert(retrieved == original)
    }

    @Test
    fun `should round-trip convert object with nullables`() = runTest {
        // given
        val original = PersonWithNullables(
            name = "Bob",
            age = null,
            email = null
        )

        // when
        val retrieved = neo4j.write { tx ->
            tx.run(
                query = $$"CREATE (p:Person $props) RETURN p",
                parameters = mapOf("props" to original.toProperties())
            ).single()["p"].toObject<PersonWithNullables>()
        }

        // then
        assert(retrieved == original)
    }

}
