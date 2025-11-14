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

import com.xemantic.kotlin.core.use
import com.xemantic.kotlin.test.assert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Examples showed in the README.md file.
 *
 * All code snippets in the README should be copied from these tests
 * to ensure they compile and work correctly.
 */
class ReadmeExamples {

    private val driver = TestNeo4j.driver

    private val neo4j = DispatchedNeo4jOperations(
        driver = TestNeo4j.driver,
        dispatcher = Dispatchers.IO.limitedParallelism(90)
    )

    @AfterEach
    fun cleanUp() {
        TestNeo4j.cleanDatabase()
    }

    // =====================================================
    // QUICK START
    // =====================================================

    @Test
    fun `Quick Start - minimal example`() = runTest {
        // Connect to Neo4j
        val neo4j = DispatchedNeo4jOperations(
            driver = driver,
            dispatcher = Dispatchers.IO.limitedParallelism(90)
        )

        // Write data
        neo4j.write { tx ->
            tx.run("CREATE (p:Person {name: 'Alice', age: 30})")
        }

        // Read data
        val name = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p.name AS name"
            ).single()["name"].asString()
        }

        println(name) // "Alice"
        assert(name == "Alice")
    }

    // =====================================================
    // CONNECTION
    // =====================================================

    @Test
    fun `Connect to the database`() {
        // Note: In real usage, provide actual connection details
        // URI examples: "neo4j://localhost", "neo4j+s://xxx.databases.neo4j.io"
        val dbUri = "<database-uri>"
        val dbUser = "<username>"
        val dbPassword = "<password>"

        // This example shows the pattern - actual connection in tests uses TestNeo4j
        // driver = GraphDatabase.driver(
        //     dbUri,
        //     AuthTokens.basic(dbUser, dbPassword)
        // ).use { driver ->
        //     driver.verifyConnectivity()
        //     println("Connection established.")
        //     // use the driver
        // }
    }

    // =====================================================
    // NEO4J OPERATIONS
    // =====================================================

    @Test
    fun `Create Neo4jOperations instance`() {
        val neo4j = DispatchedNeo4jOperations(
            driver = driver,
            dispatcher = Dispatchers.IO.limitedParallelism(90),
            defaultSessionConfig = {
                database = "neo4j"
            },
            defaultTransactionConfig = {
                timeout = 30.seconds
            }
        )
        assertNotNull(neo4j)
    }

    // =====================================================
    // BASIC CRUD OPERATIONS
    // =====================================================

    @Test
    fun `Simple read query`() = runTest {
        neo4j.populate("CREATE (p:Person {name: 'Bob'})")

        val peopleCount = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN count(p) AS count"
            ).single()["count"].asInt()
        }

        println(peopleCount)
        assert(peopleCount == 1)
    }

    @Test
    fun `Simple write query`() = runTest {
        val summary = neo4j.write { tx ->
            tx.run(
                query = $$"""
                    CREATE (a:Person {name: $name})
                    CREATE (b:Person {name: $friendName})
                    CREATE (a)-[:KNOWS]->(b)
                """.trimIndent(),
                parameters = mapOf(
                    "name" to "Alice",
                    "friendName" to "David"
                )
            ).consume()
        }
        println(summary)
        assert(summary.counters().nodesCreated() == 2)
    }

    @Test
    fun `Write query without returning summary`() = runTest {
        neo4j.write { tx ->
            tx.run(
                query = $$"""
                    CREATE (a:Person {name: $name})
                    CREATE (b:Person {name: $friendName})
                    CREATE (a)-[:KNOWS]->(b)
                """.trimIndent(),
                parameters = mapOf(
                    "name" to "Alice",
                    "friendName" to "David"
                )
            )
        }

        val count = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN count(p) AS count"
            ).single()["count"].asInt()
        }
        assert(count == 2)
    }

    // =====================================================
    // OBJECT MAPPING
    // =====================================================

    @Serializable
    data class Person(
        val name: String,
        val email: String,
        val age: Int,
        val city: String,
        val skills: List<String>,
        val active: Boolean,
        val createdAt: Instant = Clock.System.now()
    )

    @Test
    fun `Write object to database`() = runTest {
        val person = Person(
            name = "Alice Johnson",
            email = "alice.johnson@email.com",
            age = 28,
            city = "New York",
            skills = listOf("Python", "JavaScript", "SQL"),
            active = true
        )

        val createdPerson = neo4j.write { tx ->
            tx.run(
                query = $$"""
                    CREATE (person:Person $props)
                    SET person.createdAt = datetime.realtine()
                    RETURN person
                """,
                parameters = mapOf(
                    "props" to person.toProperties()
                )
            ).single()["person"].toObject<Person>()
        }

        println(createdPerson)
        assert(createdPerson.name == "Alice Johnson")
        assert(createdPerson.createdAt >= person.createdAt)
    }

    @Test
    fun `Read object from database`() = runTest {
        neo4j.populate("""
            CREATE (p:Person {
                name: 'Alice Johnson',
                email: 'alice.johnson@email.com',
                age: 28,
                city: 'New York',
                skills: ['Python', 'JavaScript', 'SQL'],
                active: true,
                createdAt: datetime()
            })
        """.trimIndent())

        val storedPerson = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p"
            ).single()["p"].toObject<Person>()
        }

        println(storedPerson)
        assert(storedPerson.name == "Alice Johnson")
    }

    // =====================================================
    // FLOW-BASED STREAMING
    // =====================================================

    @Test
    fun `Stream records as Flow`() = runTest {
        neo4j.populate("""
            CREATE (p1:Person {name: 'Alice'})
            CREATE (p2:Person {name: 'Bob'})
            CREATE (p3:Person {name: 'Charlie'})
        """.trimIndent())

        neo4j.flow(
            "MATCH (p:Person) RETURN p ORDER BY p.name"
        ).collect {
            println(it["p"]["name"].asString())
        }
        // Prints: Alice, Bob, Charlie
        // Session is automatically closed after flow collection
    }

    @Test
    fun `Stream and transform records`() = runTest {
        neo4j.populate("""
            CREATE (p1:Person {name: 'Alice', age: 30})
            CREATE (p2:Person {name: 'Bob', age: 25})
            CREATE (p3:Person {name: 'Charlie', age: 35})
        """.trimIndent())

        val names = neo4j.flow(
            query = $$"MATCH (p:Person) WHERE p.age > $minAge RETURN p.name AS name ORDER BY p.name",
            parameters = mapOf("minAge" to 28)
        ).map {
            it["name"].asString()
        }.toList()

        println(names) // [Alice, Charlie]
        assert(names == listOf("Alice", "Charlie"))
    }

    // =====================================================
    // SESSION-BASED OPERATIONS
    // =====================================================

    @Test
    fun `Multiple operations on one session`() = runTest {
        neo4j.withSession { session ->
            // First: write operation
            session.executeWrite { tx ->
                tx.run("CREATE (p:Person {name: 'Alice'})")
            }

            // Then: read operation on the same session
            val count = session.executeRead { tx ->
                tx.run("MATCH (p:Person) RETURN count(p) as count")
                    .single()["count"].asInt()
            }

            println("Created and counted: $count")
            assert(count == 1)
        }
    }

    // =====================================================
    // LOWER-LEVEL DRIVER API
    // =====================================================

    @Test
    fun `Session-based write`() = runTest {
        val summary = driver.coroutineSession().use { session ->
            session.executeWrite { tx ->
                tx.run(
                    query = $$"""
                        CREATE (a:Person {name: $name})
                        CREATE (b:Person {name: $friendName})
                        CREATE (a)-[:KNOWS]->(b)
                    """.trimIndent(),
                    parameters = mapOf(
                        "name" to "Alice",
                        "friendName" to "David"
                    )
                ).consume()
            }
        }

        println(
            "Created ${summary.counters().nodesCreated()} nodes " +
            "in ${summary.resultAvailableAfter(TimeUnit.MILLISECONDS)} ms."
        )
        assert(summary.counters().nodesCreated() == 2)
    }

    @Test
    fun `Session-based read`() = runTest {
        neo4j.populate("""
            CREATE (a:Person {name: 'Alice'})
            CREATE (d:Person {name: 'David'})
            CREATE (a)-[:KNOWS]->(d)
        """.trimIndent())

        val (names, readSummary) = driver.coroutineSession().use { session ->
            session.executeRead { tx ->
                val result = tx.run(
                    "MATCH (p:Person)-[:KNOWS]->(:Person) RETURN p.name AS name"
                )
                val names = result.records().toList().map {
                    it["name"].asString()
                }
                val summary = result.consume()
                names to summary
            }
        }

        println(
            "The query ${readSummary.query().text()} " +
            "returned ${names.size} records " +
            "in ${readSummary.resultAvailableAfter(TimeUnit.MILLISECONDS)} ms."
        )
        println("Returned names: $names")
        assert(names == listOf("Alice"))
    }

    @Test
    fun `Session and transaction configuration`() = runTest {
        neo4j.populate("CREATE (p:Person {name: 'Test'})")

        driver.coroutineSession { // session config
            database = "neo4j"
        }.use { session ->
            session.executeRead({ // transaction config
                timeout = 5.seconds
                metadata = mapOf("appName" to "peopleTracker")
            }) { tx ->
                tx.run("MATCH (p:Person) RETURN p").records().collect {
                    println(it)
                }
            }
        }
    }

    // =====================================================
    // POPULATE UTILITY
    // =====================================================

    @Test
    fun `Use populate for test data`() = runTest {
        neo4j.populate("""
            CREATE (p1:Person {name: 'Alice', age: 30})
            CREATE (p2:Person {name: 'Bob', age: 25})
            CREATE (p1)-[:KNOWS]->(p2)
        """.trimIndent())

        val count = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN count(p) AS count"
            ).single()["count"].asInt()
        }

        assert(count == 2)
    }

}