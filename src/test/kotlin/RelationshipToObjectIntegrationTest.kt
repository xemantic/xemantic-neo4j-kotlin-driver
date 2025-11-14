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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Integration tests for [toObject] extension function.
 * These tests verify conversion from Neo4j relationships to Kotlin objects.
 */
class RelationshipToObjectIntegrationTest {

    private val neo4j = DispatchedNeo4jOperations(
        driver = TestNeo4j.driver,
        dispatcher = Dispatchers.IO.limitedParallelism(90)
    )

    @AfterEach
    fun cleanDatabase() {
        TestNeo4j.cleanDatabase()
    }

    @Test
    fun `Relationship toObject should convert relationship properties`() = runTest {
        // given
        neo4j.populate("""
            CREATE (a:Person {name: 'Alice'})
            CREATE (b:Person {name: 'Bob'})
            CREATE (a)-[:KNOWS {since: 2020}]->(b)
        """.trimIndent())

        // when
        val knows = neo4j.read { tx ->
            tx.run(
                "MATCH ()-[r:KNOWS]->() RETURN r"
            ).single()["r"].toObject<KnowsSince>()
        }

        // then
        knows should {
            have(since == 2020)
        }
    }

    @Test
    fun `Relationship toObject should handle complex relationship properties`() = runTest {
        // given
        neo4j.populate("""
            CREATE (p:Person {name: 'Alice'})
            CREATE (c:Company {name: 'Acme Corp'})
            CREATE (p)-[:WORKS_FOR {
                position: 'Software Engineer',
                since: 2020,
                salary: 100000
            }]->(c)
        """.trimIndent())

        // when
        val worksFor = neo4j.read { tx ->
            tx.run(
                "MATCH ()-[r:WORKS_FOR]->() RETURN r"
            ).single()["r"].toObject<WorksFor>()
        }

        // then
        worksFor should {
            have(position == "Software Engineer")
            have(since == 2020)
            have(salary == 100000L)
        }
    }

    @Test
    fun `Relationship toObject should handle nullable properties`() = runTest {
        // given
        neo4j.populate("""
            CREATE (p:Person {name: 'Alice'})
            CREATE (c:Company {name: 'Acme Corp'})
            CREATE (p)-[:WORKS_FOR {
                position: 'Intern',
                since: 2023
            }]->(c)
        """.trimIndent())

        // when
        val worksFor = neo4j.read { tx ->
            tx.run(
                "MATCH ()-[r:WORKS_FOR]->() RETURN r"
            ).single()["r"].toObject<WorksFor>()
        }

        // then
        worksFor should {
            have(position == "Intern")
            have(since == 2023)
            have(salary == null)
        }
    }

}
