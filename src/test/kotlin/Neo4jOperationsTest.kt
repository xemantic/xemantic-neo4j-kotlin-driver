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
import com.xemantic.kotlin.test.coroutines.should
import com.xemantic.kotlin.test.have
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.neo4j.driver.Query
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Tests for [Neo4jOperations] interface demonstrating the recommended pattern
 * for accessing Neo4j with this library.
 *
 * These tests showcase the higher-level API that provides safer, more convenient
 * access to Neo4j compared to using Driver.coroutineSession() directly.
 */
class Neo4jOperationsTest {

    private val neo4j = DispatchedNeo4jOperations(
        driver = TestNeo4j.driver,
        dispatcher = Dispatchers.IO.limitedParallelism(90)
    )

    @AfterEach
    fun cleanUp() {
        TestNeo4j.cleanDatabase()
    }

    // ========================================
    // Basic CRUD Operations
    // ========================================

    @Test
    fun `should perform write and read using shortcuts with default configs`() = runTest {
        // given: write using neo4j.write shortcut
        neo4j.write { tx ->
            tx.run(
                query = $$"""CREATE (a:Person {name: $name, age: $age})""",
                parameters = mapOf(
                    "name" to "Bob",
                    "age" to 42
                )
            )
        }

        // when: read using neo4j.read shortcut
        val (name, age) = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person {name: 'Bob'}) RETURN p.name AS name, p.age AS age"
            ).single().let {
                it["name"].asString() to it["age"].asInt()
            }
        }

        // then
        assert(name == "Bob")
        assert(age == 42)
    }

    @Test
    fun `should perform write and read using shortcuts with explicit configs`() = runTest {
        // given: write using neo4j.write shortcut with explicit TransactionConfig
        val txConfig = TransactionConfig {
            timeout = 5.seconds
        }

        neo4j.write(transactionConfig = txConfig) { tx ->
            tx.run("CREATE (a:Person {name: 'Charlie', age: 30})")
        }

        // when: read using neo4j.read shortcut with explicit TransactionConfig
        val name = neo4j.read(transactionConfig = txConfig) { tx ->
            tx.run(
                "MATCH (p:Person {name: 'Charlie'}) RETURN p.name AS name"
            ).single()["name"].asString()
        }

        // then
        assert(name == "Charlie")
    }

    // ========================================
    // Flow-based Streaming (Session-based)
    // ========================================

    @Test
    fun `should store and retrieve nodes using Flow collection`() = runTest {
        // given: a person is created in the database
        @Serializable
        data class Person(
            val id: String,
            val name: String,
            val email: String,
            val age: Int,
            val city: String,
            val skills: List<String>,
            val active: Boolean,
            val createdAt: Instant
        )

        neo4j.populate("""
            CREATE (alice:Person {
                id: 'p001',
                name: 'Alice Johnson',
                email: 'alice.johnson@email.com',
                age: 28,
                city: 'New York',
                skills: ['Python', 'JavaScript', 'SQL'],
                active: true,
                createdAt: datetime('2023-01-15T10:30:00')
            });
        """.trimIndent())

        // when: querying the database and collecting the Flow of records
        val people = neo4j.flow(
            "MATCH (p:Person) RETURN p"
        ).map {
            it["p"].toObject<Person>()
        }.toList()

        // then: the person data matches what was stored
        assert(people.size == 1)
        people[0] should {
            have(id == "p001")
            have(name == "Alice Johnson")
            have(email == "alice.johnson@email.com")
            have(age == 28)
            have(city == "New York")
            have(skills == listOf("Python", "JavaScript", "SQL"))
            have(active)
            have(createdAt == Instant.parse("2023-01-15T10:30:00Z"))
        }
    }

    @Test
    fun `should stream and transform multiple nodes using flow`() = runTest {
        // given: multiple people in the database
        neo4j.populate("""
            CREATE (p1:Person {name: 'Alice', age: 30, city: 'New York'})
            CREATE (p2:Person {name: 'Bob', age: 25, city: 'London'})
            CREATE (p3:Person {name: 'Charlie', age: 35, city: 'Tokyo'})
        """.trimIndent())

        // when: streaming and transforming data
        val cities = neo4j.flow { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p.city AS city ORDER BY p.city"
            ).records()
        }.map {
            it["city"].asString().uppercase()
        }.toList()

        // then
        assert(cities == listOf("LONDON", "NEW YORK", "TOKYO"))
    }

    @Test
    fun `should perform write and read operations in session-based flow`() = runTest {
        // given: initial data
        neo4j.populate("""
            CREATE (p1:Person {name: 'Alice', age: 30})
            CREATE (p2:Person {name: 'Bob', age: 25})
        """.trimIndent())

        // when: using session-based flow to write and then read
        val names = neo4j.flow { session ->
            // First: write new data in a write transaction
            session.executeWrite { tx ->
                tx.run("""
                    CREATE (p:Person {name: 'Charlie', age: 35, processedAt: datetime()})
                    CREATE (p2:Person {name: 'Diana', age: 28, processedAt: datetime()})
                """.trimIndent())
            }

            // Then: read all data using session.flow() convenience method
            session.flow("MATCH (p:Person) RETURN p.name AS name ORDER BY p.name")
        }.map {
            it["name"].asString()
        }.toList()

        // then: should have both original and newly written data
        assert(names == listOf("Alice", "Bob", "Charlie", "Diana"))

        // and: verify the written data exists and has processedAt
        val newPeopleCount = neo4j.read { tx ->
            tx.run("MATCH (p:Person) WHERE p.processedAt IS NOT NULL RETURN count(p) AS count")
                .single()["count"].asInt()
        }

        assert(newPeopleCount == 2)
    }

    // ========================================
    // Flow-based Streaming (Query-based convenience methods)
    // ========================================

    @Test
    fun `should stream records using flow with Query object`() = runTest {
        // given: test data
        neo4j.populate("""
            CREATE (p1:Person {name: 'Alice', age: 30})
            CREATE (p2:Person {name: 'Bob', age: 25})
            CREATE (p3:Person {name: 'Charlie', age: 35})
        """.trimIndent())

        // when: using flow with Query object
        val query = Query(
            $$"MATCH (p:Person) WHERE p.age >= $minAge RETURN p.name AS name, p.age AS age ORDER BY p.age",
            mapOf("minAge" to 30)
        )

        data class PersonData(val name: String, val age: Int)

        val people = neo4j.flow(query).map {
            PersonData(
                name = it["name"].asString(),
                age = it["age"].asInt()
            )
        }.toList()

        // then
        assert(people.size == 2)
        people[0] should {
            have(name == "Alice")
            have(age == 30)
        }
        people[1] should {
            have(name == "Charlie")
            have(age == 35)
        }
    }

    @Test
    fun `should stream records using flow with parameterized query string`() = runTest {
        // given: test data
        neo4j.populate("""
            CREATE (p1:Person {name: 'Alice', age: 30, city: 'New York'})
            CREATE (p2:Person {name: 'Bob', age: 25, city: 'London'})
            CREATE (p3:Person {name: 'Charlie', age: 35, city: 'New York'})
            CREATE (p4:Person {name: 'Diana', age: 28, city: 'Paris'})
        """.trimIndent())

        // when: using flow with parameterized string query
        val names = neo4j.flow(
            query = $$"MATCH (p:Person) WHERE p.city = $city AND p.age >= $minAge RETURN p.name AS name ORDER BY p.name",
            parameters = mapOf(
                "city" to "New York",
                "minAge" to 28
            )
        ).map {
            it["name"].asString()
        }.toList()

        // then
        assert(names == listOf("Alice", "Charlie"))
    }

    @Test
    fun `should stream records using flow with simple query string`() = runTest {
        // given: test data
        neo4j.populate("""
            CREATE (p1:Person {name: 'Alice', age: 30})
            CREATE (p2:Person {name: 'Bob', age: 25})
            CREATE (p3:Person {name: 'Charlie', age: 35})
        """.trimIndent())

        // when: using flow with simple string query (no parameters)
        val names = neo4j.flow(
            "MATCH (p:Person) RETURN p.name AS name ORDER BY p.name"
        ).map {
            it["name"].asString()
        }.toList()

        // then
        assert(names == listOf("Alice", "Bob", "Charlie"))
    }

    @Test
    fun `should handle empty results with query-based flow`() = runTest {
        // given: empty database
        // (no data populated)

        // when: querying with no matches
        val results = neo4j.flow(
            "MATCH (p:Person) RETURN p"
        ).toList()

        // then
        assert(results.isEmpty())
    }

    @Test
    fun `should stream large result sets with query-based flow`() = runTest {
        // given: large dataset
        neo4j.populate("""
            UNWIND range(1, 1000) AS id
            CREATE (:Person {id: id, name: 'Person ' + id})
        """.trimIndent())

        // when: streaming large result set
        val count = neo4j.flow(
            "MATCH (p:Person) RETURN p.id AS id"
        ).map {
            it["id"].asInt()
        }.toList()
            .size

        // then
        assert(count == 1000)
    }

    @Test
    fun `should use custom configs with query-based flow`() = runTest {
        // given: test data and custom configs
        neo4j.populate("CREATE (p:Person {name: 'Test'})")

        val sessionConfig = SessionConfig {
            database = "neo4j"
            fetchSize = 100L
        }

        val txConfig = TransactionConfig {
            timeout = 5.seconds
        }

        // when: using flow with custom configurations
        val name = neo4j.flow(
            query = "MATCH (p:Person) RETURN p.name AS name",
            sessionConfig = sessionConfig,
            transactionConfig = txConfig
        ).first()["name"].asString()

        // then
        assert(name == "Test")
    }

    // ========================================
    // Test Data Population
    // ========================================

    @Test
    fun `should populate database using populate shortcut`() = runTest {
        // when: using populate to insert test data
        neo4j.populate(
            """
                CREATE (p1:Person {name: 'Dave', age: 35})
                CREATE (p2:Person {name: 'Eve', age: 28})
                CREATE (p1)-[:KNOWS]->(p2)
            """.trimIndent()
        )

        // then: data should be accessible
        val count = neo4j.read { tx ->
            tx.run("MATCH (p:Person) RETURN count(p) AS count")
                .single()["count"].asInt()
        }

        assert(count == 2)

        // and: relationships should exist
        val relationshipCount = neo4j.read { tx ->
            tx.run("MATCH ()-[r:KNOWS]->() RETURN count(r) AS count")
                .single()["count"].asInt()
        }

        assert(relationshipCount == 1)
    }

    @Test
    fun `should populate database with custom configs`() = runTest {
        // given
        val txConfig = TransactionConfig {
            timeout = 5.seconds
        }

        // when: using populate with transaction config
        neo4j.populate(
            query = "CREATE (p:Person {name: 'Frank', age: 40})",
            transactionConfig = txConfig
        )

        // then: data should be accessible
        val name = neo4j.read { tx ->
            tx.run("MATCH (p:Person {name: 'Frank'}) RETURN p.name AS name")
                .single()["name"].asString()
        }

        assert(name == "Frank")
    }

    // ========================================
    // Complex Queries
    // ========================================

    @Test
    fun `should handle aggregations and grouping`() = runTest {
        // given: people in different cities
        neo4j.populate("""
            CREATE (p1:Person {name: 'Alice', city: 'New York', age: 30})
            CREATE (p2:Person {name: 'Bob', city: 'London', age: 25})
            CREATE (p3:Person {name: 'Charlie', city: 'New York', age: 35})
            CREATE (p4:Person {name: 'Diana', city: 'London', age: 28})
        """.trimIndent())

        // when: querying aggregated data by city
        data class CityStats(val city: String, val count: Int, val avgAge: Double)

        val stats = neo4j.flow("""
            MATCH (p:Person)
            RETURN p.city AS city,
                   count(p) AS count,
                   avg(p.age) AS avgAge
            ORDER BY city
        """.trimIndent()).map {
            CityStats(
                city = it["city"].asString(),
                count = it["count"].asInt(),
                avgAge = it["avgAge"].asDouble()
            )
        }.toList()

        // then
        assert(stats.size == 2)
        stats[0] should {
            have(city == "London")
            have(count == 2)
            have(avgAge == 26.5)
        }
        stats[1] should {
            have(city == "New York")
            have(count == 2)
            have(avgAge == 32.5)
        }
    }

    @Test
    fun `should handle relationships and pattern matching`() = runTest {
        // given: a social network
        neo4j.populate("""
            CREATE (alice:Person {name: 'Alice', age: 30})
            CREATE (bob:Person {name: 'Bob', age: 25})
            CREATE (charlie:Person {name: 'Charlie', age: 35})
            CREATE (alice)-[:KNOWS {since: 2020}]->(bob)
            CREATE (bob)-[:KNOWS {since: 2021}]->(charlie)
            CREATE (alice)-[:KNOWS {since: 2019}]->(charlie)
        """.trimIndent())

        // when: finding mutual friends
        data class Connection(
            val person1: String,
            val person2: String,
            val mutualFriend: String
        )

        val connections = neo4j.flow("""
            MATCH (p1:Person)-[:KNOWS]->(mutual:Person)<-[:KNOWS]-(p2:Person)
            WHERE p1.name < p2.name
            RETURN p1.name AS person1,
                   p2.name AS person2,
                   mutual.name AS mutualFriend
            ORDER BY person1, person2
        """.trimIndent()).map {
            Connection(
                person1 = it["person1"].asString(),
                person2 = it["person2"].asString(),
                mutualFriend = it["mutualFriend"].asString()
            )
        }.toList()

        // then: Alice and Bob have Charlie as mutual friend
        assert(connections.size == 1)
        connections[0] should {
            have(person1 == "Alice")
            have(person2 == "Bob")
            have(mutualFriend == "Charlie")
        }
    }

    // ========================================
    // Session Configuration
    // ========================================

    @Test
    fun `should use custom session configuration`() = runTest {
        // given: custom session config
        val sessionConfig = SessionConfig {
            database = "neo4j"
            fetchSize = 1000L
        }

        neo4j.populate(
            query = "CREATE (p:Person {name: 'TestUser'})",
            sessionConfig = sessionConfig
        )

        // when: reading with custom session config
        val name = neo4j.read(sessionConfig = sessionConfig) { tx ->
            tx.run("MATCH (p:Person {name: 'TestUser'}) RETURN p.name AS name")
                .single()["name"].asString()
        }

        // then
        assert(name == "TestUser")
    }

    // ========================================
    // Error Handling
    // ========================================

    @Test
    fun `should handle transactions with automatic rollback on error`() = runTest {
        // when: transaction that throws an exception
        try {
            neo4j.write { tx ->
                tx.run("CREATE (p:Person {name: 'WillBeRolledBack'})")
                throw IllegalStateException("Simulated error")
            }
        } catch (e: IllegalStateException) {
            // expected
        }

        // then: data should not exist (transaction was rolled back)
        val count = neo4j.read { tx ->
            tx.run("MATCH (p:Person {name: 'WillBeRolledBack'}) RETURN count(p) AS count")
                .single()["count"].asInt()
        }

        assert(count == 0)
    }

    // ========================================
    // Multiple Operations
    // ========================================

    @Test
    fun `should chain multiple operations in sequence`() = runTest {
        // when: performing multiple sequential operations

        // 1. Create initial data
        neo4j.populate("CREATE (p:Person {name: 'Alice', score: 0})")

        // 2. Update data
        neo4j.write { tx ->
            tx.run("""
                MATCH (p:Person {name: 'Alice'})
                SET p.score = 100
            """.trimIndent())
        }

        // 3. Read and verify
        val score = neo4j.read { tx ->
            tx.run("MATCH (p:Person {name: 'Alice'}) RETURN p.score AS score")
                .single()["score"].asInt()
        }

        // then
        assert(score == 100)
    }

    @Test
    fun `should perform batch operations efficiently`() = runTest {
        // given: batch data creation
        neo4j.write { tx ->
            tx.run("""
                UNWIND range(1, 100) AS id
                CREATE (:Person {id: id, name: 'Person ' + id, active: true})
            """.trimIndent())
        }

        // when: batch update
        neo4j.write { tx ->
            tx.run("""
                MATCH (p:Person)
                WHERE p.id % 2 = 0
                SET p.active = false
            """.trimIndent())
        }

        // then: verify counts
        val activeCount = neo4j.read { tx ->
            tx.run("MATCH (p:Person {active: true}) RETURN count(p) AS count")
                .single()["count"].asInt()
        }
        val inactiveCount = neo4j.read { tx ->
            tx.run("MATCH (p:Person {active: false}) RETURN count(p) AS count")
                .single()["count"].asInt()
        }

        assert(activeCount == 50)
        assert(inactiveCount == 50)
    }

    @Test
    fun `should handle unmanaged transaction with manual commit`() = runTest {
        neo4j.withSession { session ->
            // given
            val tx = session.beginTransaction()

            // when
            tx.run("CREATE (p:Person {name: 'Frank'})").consume()

            tx.commit()

            tx.close()

            // then
            assert(!tx.isOpen()) // committed
            val count = session.run(
                "MATCH (p:Person) WHERE p.name = 'Frank' RETURN count(p) AS count"
            ).single()["count"].asInt()

            assert(count == 1)
        }

        // verify with another session
        val count = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) WHERE p.name = 'Frank' RETURN count(p) AS count"
            ).single()["count"].asInt()
        }
        assert(count == 1)

    }

}