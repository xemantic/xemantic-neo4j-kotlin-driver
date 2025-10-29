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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.exceptions.NoSuchRecordException
import org.neo4j.driver.exceptions.ResultConsumedException
import org.neo4j.driver.summary.QueryType
import org.neo4j.harness.Neo4j
import org.neo4j.harness.junit.extension.Neo4jExtension
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.measureTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(Neo4jExtension::class)
class Neo4jCoroutineDriverTest {

    lateinit var driver: Driver

    @BeforeAll
    fun setUpDriver(neo4j: Neo4j) {
        driver = GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none())
    }

    @AfterEach
    fun cleanDatabase() {
        driver.executableQuery("MATCH (n) DETACH DELETE n").execute()
    }

    @Test
    fun `README example`() = runTest {

        // Write to the database

        val summary = driver.coroutineSession().use { session ->
            session.executeWrite { tx ->
                tx.run(
                    // multi-dollar interpolation allows to include $ without escaping
                    query = $$"""
                        CREATE (a:Person {name: $name})
                        CREATE (b:Person {name: $friendName})
                        CREATE (a)-[:KNOWS]->(b)
                    """.trimIndent(),
                    // named `query` and `parameters` parameters can be skipped if you prefer
                    parameters = mapOf(
                        "name" to "Alice",
                        "friendName" to "David"
                    )
                ).consume() // ensures that the ResultSummary is returned
            }
        }

        println(
            "Created ${summary.counters().nodesCreated()} " +
                    "in ${summary.resultAvailableAfter(TimeUnit.MILLISECONDS)} ms."
        )

        // Read from the database

        val (names, readSummary) = driver.coroutineSession().use { session ->
            session.executeRead { tx ->
                val result = tx.run(
                    "MATCH (p:Person)-[:KNOWS]->(:Person) RETURN p.name AS name"
                )
                val names = result.records().map {
                    it["name"].asString()
                }.toList()
                val summary = result.consume()
                names to summary
            }
        }

        println(
            "The query ${readSummary.query()} " +
                    "returned ${names.size} records " +
                    "in ${readSummary.resultAvailableAfter(TimeUnit.MILLISECONDS)} ms."
        )
        println("Returned names: $names")

    }

    @Test
    fun `should perform simple write - README example`() = runTest {

        val summary = driver.coroutineSession().use { session ->
            session.executeWrite { tx ->
                tx.run(
                    // multi-dollar interpolation allows to include $ without escaping
                    query = $$"""
                        CREATE (a:Person {name: $name})
                        CREATE (b:Person {name: $friendName})
                        CREATE (a)-[:KNOWS]->(b)
                    """.trimIndent(),
                    // named `query` and `parameters` parameters can be skipped if you prefer
                    parameters = mapOf(
                        "name" to "Alice",
                        "friendName" to "David"
                    )
                ).consume() // ensures that the ResultSummary is returned
            }
        }

        println(
            "Created ${summary.counters().nodesCreated()}" +
                    " in ${summary.resultAvailableAfter(TimeUnit.MILLISECONDS)} ms."
        )
        assert(summary.counters().nodesCreated() == 2)
    }

    // given
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

    @Test
    fun `should store and retrieve a node using Flow collection`() = runTest {

        // given: a person is created in the database
        driver.populate("""
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
        val person = driver.coroutineSession().use { session ->
            session.executeRead { tx ->
                tx.run(
                    "MATCH (p:Person) RETURN p"
                ).records().map { record ->
                    record["p"].let { p ->
                        Person(
                            id =        p["id"].asString(),
                            name =      p["name"].asString(),
                            email =     p["email"].asString(),
                            age =       p["age"].asInt(),
                            city =      p["city"].asString(),
                            skills =    p["skills"].asList { it.asString() },
                            active =    p["active"].asBoolean(),
                            createdAt = p["createdAt"].asInstant()
                        )
                    }
                }.first()
            }
        }

        // then: the person data matches what was stored
        person should {
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
    fun `should return keys from Result`() = runTest {
        // given: a query that returns specific columns
        driver.populate("CREATE (p:Person {name: 'Bob', age: 30})")

        // when: running a query and getting keys
        val result = driver.coroutineSession().use { session ->
            session.executeRead { tx ->
                tx.run("MATCH (p:Person) RETURN p.name AS name, p.age AS age")
            }
        }

        // then
        assert(result.keys() == listOf("name", "age"))
    }

    @Test
    fun `should return Result from executeWrite`() = runTest {
        // when
        val result = driver.coroutineSession().use { session ->
            session.executeWrite { tx ->
                tx.run("CREATE (p:Person {name: 'Charlie'})")
            }
        }

        // then
        assertFailsWith<ResultConsumedException> {
            result.records().collect() // we cannot collect anymore
        }
        result should {
            have(keys().isEmpty())
            have(!isOpen())
            consume() should { // we can consume again to get ResultSummary
                have(queryType() == QueryType.WRITE_ONLY)
            }
        }

    }

    @Test
    fun `should return ResultSummary from executeWrite when consumed`() = runTest {
        // when
        val summary = driver.coroutineSession().use { session ->
            session.executeWrite { tx ->
                tx.run("CREATE (p:Person {name: 'Charlie'})").consume()
            }
        }

        // then
        summary should {
            have(queryType() == QueryType.WRITE_ONLY)
            counters() should {
                have(containsUpdates())
                have(nodesCreated() == 1)
            }
        }

    }

    @Test
    fun `should consume result and return summary`() = runTest {
        // given: a node creation query
        driver.populate("CREATE (p:Person {name: 'Charlie'})")

        // when: running a query and consuming without collecting records
        val result = driver.coroutineSession().executeRead { tx ->
            tx.run("MATCH (p:Person) WHERE p.name = 'Charlie' RETURN p")
        }
        val summary = result.consume()

        // then: Summary is returned without collecting records
        summary should {
            have(queryType() == QueryType.READ_ONLY)
            counters() should {
                have(!containsUpdates())
                have(nodesCreated() == 0)
                have(nodesDeleted() == 0)
                have(relationshipsCreated() == 0)
                have(relationshipsDeleted() == 0)
                have(propertiesSet() == 0)
                have(labelsAdded() == 0)
                have(labelsRemoved() == 0)
                have(indexesAdded() == 0)
                have(indexesRemoved() == 0)
                have(constraintsAdded() == 0)
                have(constraintsRemoved() == 0)
                have(!containsSystemUpdates())
                have(systemUpdates() == 0)
            }
        }
    }

    @Test
    fun `should use managed transaction with executeWrite and executeRead`() = runTest {
        // given: data in the database
        driver.populate("CREATE (p:Person {name: 'Dana', age: 25})")

        // when: using executeRead for a read transaction
        val count = driver.coroutineSession().executeRead { tx ->
            tx.run("MATCH (p:Person) WHERE p.name = 'Dana' RETURN count(p) AS count")
                .records()
                .count()
        }

        // then
        assert(count == 1)
    }

    @Test
    fun `should handle unmanaged transaction with manual commit`() = runTest {

        driver.coroutineSession().use { session ->
            // given
            val tx = session.beginTransaction()

            // when
            tx.run("CREATE (p:Person {name: 'Frank'})").consume()

            tx.commit()

            tx.close()

            // then
            assert(!tx.isOpen()) // committed
            val count = session.run("MATCH (p:Person) WHERE p.name = 'Frank' RETURN count(p) AS count")
                .records()
                .count()

            assert(count == 1)
        }

        // verify with another session
        driver.coroutineSession().use { session ->
            val count = session.run("MATCH (p:Person) WHERE p.name = 'Frank' RETURN count(p) AS count")
                .records()
                .count()
            assert(count == 1)
        }

    }

    @Test
    fun `should handle unmanaged transaction with manual rollback`() = runTest {
        // When: Using beginTransaction with manual rollback
        val session = driver.coroutineSession()
        val tx = session.beginTransaction()

        tx.run("CREATE (p:Person {name: 'Grace'})").consume()
        tx.rollback()

        // Then: The node was NOT created
        val exists = session.executeRead { tx ->
            tx.run("MATCH (p:Person) WHERE p.name = 'Grace' RETURN count(p) AS count")
                .records()
                .map { it["count"].asLong() }
                .first()
        }

        assert(exists == 0L)
    }

    @Test
    fun `should check if transaction is open`() = runTest {
        driver.coroutineSession().use { session ->

            // given
            val tx = session.beginTransaction()
            assert(tx.isOpen())

            // when
            tx.commit()

            // then
            assert(!tx.isOpen())
        }

    }

    @Test
    fun `should check if result is open`() = runTest {
        // given
        val result = driver.coroutineSession().run(
            "RETURN 1 AS value"
        )
        assert(result.isOpen())

        // when
        result.consume()

        // then
        assert(!result.isOpen())
    }

    @Test
    fun `should allow multiple calls to consume() returning cached summary`() = runTest {
        // given
        val result = driver.coroutineSession().run(
            "RETURN 1 AS value"
        )

        // when: calling consume multiple times
        val summary1 = result.consume()
        val summary2 = result.consume()

        // then: Both summaries should have the same query type
        summary1 should {
            have(queryType() == QueryType.READ_ONLY)
        }
        summary2 should {
            have(queryType() == QueryType.READ_ONLY)
        }
    }

    @Test
    fun `should throw ResultConsumedException when collecting records after consume()`() = runTest {
        // given: data in the database
        driver.populate("CREATE (p:Person {name: 'TestPerson', age: 42})")

        // when: getting a result, consuming it first, then trying to collect records
        val result = driver.coroutineSession().run(
            "MATCH (p:Person) WHERE p.name = 'TestPerson' RETURN p"
        )

        // Consume the result first
        result.consume()

        // then: attempting to collect records should throw ResultConsumedException
        assertFailsWith<ResultConsumedException> {
            result.records().collect()
        }
    }

    @Test
    fun `should stream large amount of nodes and relationships using Flow`() = runTest {
        // given: create a large graph with many people and friendships
        val nodeCount = 1000
        val relationshipMultiplier = 3 // each person has ~3 friends

        driver.coroutineSession().use { session ->

            session.executeWrite { tx ->
                // Create nodes in batches for better performance
                tx.run("""
                    UNWIND range(1, $nodeCount) AS id
                    CREATE (:Person {
                        id: 'person_' + id,
                        name: 'Person ' + id,
                        age: 20 + (id % 50)
                    })
                """.trimIndent())

                // Create relationships - each person befriends the next few people
                tx.run("""
                    MATCH (p:Person)
                    WITH p ORDER BY p.id
                    WITH collect(p) AS people
                    UNWIND range(0, size(people) - 1) AS i
                    WITH people[i] AS person, people, i
                    UNWIND range(1, $relationshipMultiplier) AS offset
                    WITH person, people[(i + offset) % size(people)] AS friend
                    WHERE person.id < friend.id
                    CREATE (person)-[:FRIENDS_WITH {since: 2020 + (toInteger(split(person.id, '_')[1]) % 5)}]->(friend)
                """.trimIndent())
            }

        }

        // when: streaming all relationships with person data
        var recordCount = 0
        var totalAge = 0
        val friendshipYears = mutableSetOf<Int>()

        driver.coroutineSession().use { session ->
            session.executeRead { tx ->

                val result = tx.run("""
                    MATCH (p1:Person)-[f:FRIENDS_WITH]->(p2:Person)
                    RETURN p1.name AS person1, p1.age AS age1,
                           p2.name AS person2, p2.age AS age2,
                           f.since AS friendsSince
                    ORDER BY person1
                """.trimIndent())

                result should {
                    have(isOpen())
                    have(keys() == listOf("person1", "age1", "person2", "age2", "friendsSince"))
                }

                // Stream and process records using Flow
                result.records().collect { record ->
                    recordCount++
                    totalAge += record["age1"].asInt()
                    totalAge += record["age2"].asInt()
                    friendshipYears.add(record["friendsSince"].asInt())
                }

            }

        }

        // then: verify we processed a large number of records
        // Expected: nodeCount * relationshipMultiplier - losses from circular wraparound
        // Last 3 nodes lose 1, 2, 3 relationships respectively due to WHERE clause
        val expectedRelationships = nodeCount * relationshipMultiplier - (1 + 2 + 3)
        assert(recordCount > 1000)
        assert(recordCount == expectedRelationships)
        assert(totalAge > 0)
        assert(friendshipYears.size in 1..5)
    }

    @Test
    fun `should auto consume result after records Flow is collected`() = runTest {
        // given: data in the database
        driver.populate("CREATE (p:Person {name: 'Dana', age: 25})")

        // when: using executeRead for a read transaction
        driver.coroutineSession().use { session ->
            session.executeRead { tx ->
                val result = tx.run("MATCH (p:Person) WHERE p.name = 'Dana' RETURN count(p) AS count")
                val records = result.records()

                // then
                assert(result.isOpen())

                // but after
                records.count() // or any collecting function

                // then
                assert(!result.isOpen())
            }
        }
    }

    @Test
    fun `should auto consume result when collecting records Flow throws an exception`() = runTest {
        // given: data in the database
        driver.populate("CREATE (p:Person {name: 'Dana', age: 25})")

        // when: using executeRead for a read transaction
        driver.coroutineSession().use { session ->
            session.executeRead { tx ->
                val result = tx.run("MATCH (p:Person) WHERE p.name = 'Dana' RETURN count(p) AS count")
                val records = result.records()

                // then
                assert(result.isOpen())

                // but after
                try {
                    records.collect {
                        throw IllegalStateException()
                    }
                } catch (e: IllegalStateException) {
                    // then
                    assert(!result.isOpen())
                }

            }
        }
    }

    @Test
    fun `should throw exception when records is called more than once`() = runTest {

        // given: data in the database
        driver.populate("CREATE (p:Person {name: 'Eve', age: 30})")

        driver.coroutineSession().use { session ->

            // retrieved
            val result = session.run("MATCH (p:Person) WHERE p.name = 'Eve' RETURN p")
            result.records().collect() // first call succeeds

            // when
            val exception = assertFailsWith<ResultConsumedException> {
                result.records().collect()
            }

            // then
            exception should {
                have(message == "Records can only be consumed once")
            }

        }

    }

    @Test
    fun `should return single record when result has exactly one record`() = runTest {

        // given: exactly one person in the database
        driver.populate("CREATE (p:Person {name: 'SinglePerson', age: 42})")

        driver.coroutineSession().use { session ->

            // when: querying for that one person using single()
            val record = session.executeRead { tx ->
                tx.run(
                    "MATCH (p:Person) WHERE p.name = 'SinglePerson' RETURN p.name AS name, p.age AS age"
                ).single()
            }

            // then: the record should contain the expected data
            record should {
                have(get("name").asString() == "SinglePerson")
                have(get("age").asInt() == 42)
            }

        }

    }

    @Test
    fun `should throw NoSuchRecordException when single() is called with zero records`() = runTest {

        // given: no matching records in the database
        driver.populate("CREATE (p:Person {name: 'OtherPerson', age: 30})")

        driver.coroutineSession().use { session ->

            // when/then: calling single() should throw NoSuchRecordException
            assertFailsWith<NoSuchRecordException> {
                session.executeRead { tx ->
                    tx.run(
                        "MATCH (p:Person) WHERE p.name = 'NonExistent' RETURN p"
                    ).single()
                }
            }

        }

    }

    @Test
    fun `should throw NoSuchRecordException when single() is called with multiple records`() = runTest {

        // given: multiple people in the database
        driver.populate("""
            CREATE (p1:Person {name: 'Alice', age: 25})
            CREATE (p2:Person {name: 'Bob', age: 30})
            CREATE (p3:Person {name: 'Charlie', age: 35})
        """.trimIndent())

        driver.coroutineSession().use { session ->

            // when/then: calling single() should throw NoSuchRecordException
            assertFailsWith<NoSuchRecordException> {
                session.executeRead { tx ->
                    tx.run(
                        "MATCH (p:Person) RETURN p ORDER BY p.name"
                    ).single()
                }
            }

        }

    }

    @Test
    fun `should throw ResultConsumedException when single() is called after consume()`() = runTest {

        // given: data in the database
        driver.populate("CREATE (p:Person {name: 'TestPerson', age: 40})")

        driver.coroutineSession().use { session ->

            // when: getting a result, consuming it first, then trying to call single()
            val result = session.run(
                "MATCH (p:Person) WHERE p.name = 'TestPerson' RETURN p"
            )

            // Consume the result first
            result.consume()

            // then: attempting to call single() should throw ResultConsumedException
            assertFailsWith<ResultConsumedException> {
                result.single()
            }

        }

    }

    @Test
    fun `should throw ResultConsumedException when single() is called after records() collection`() = runTest {

        // given: data in the database
        driver.populate("CREATE (p:Person {name: 'AnotherPerson', age: 45})")

        driver.coroutineSession().use { session ->

            // when: getting a result, collecting records first, then trying to call single()
            val result = session.run(
                "MATCH (p:Person) WHERE p.name = 'AnotherPerson' RETURN p"
            )

            // Collect records first
            result.records().collect()

            // then: attempting to call single() should throw ResultConsumedException
            assertFailsWith<ResultConsumedException> {
                result.single()
            }

        }

    }

    @Test
    fun `should return single record when singleOrNull() is called with exactly one record`() = runTest {

        // given: exactly one person in the database
        driver.populate("CREATE (p:Person {name: 'OnlyPerson', age: 50})")

        driver.coroutineSession().use { session ->

            // when: querying for that one person using singleOrNull()
            val record = session.executeRead { tx ->
                tx.run(
                    "MATCH (p:Person) WHERE p.name = 'OnlyPerson' RETURN p.name AS name, p.age AS age"
                ).singleOrNull()
            }

            // then: the record should contain the expected data
            record should {
                have(get("name").asString() == "OnlyPerson")
                have(get("age").asInt() == 50)
            }

        }

    }

    @Test
    fun `should return null when singleOrNull() is called with zero records`() = runTest {

        // given: no matching records in the database
        driver.populate("CREATE (p:Person {name: 'SomeOtherPerson', age: 25})")

        driver.coroutineSession().use { session ->

            // when: calling singleOrNull() with no matching records
            val record = session.executeRead { tx ->
                tx.run(
                    "MATCH (p:Person) WHERE p.name = 'NonExistentPerson' RETURN p"
                ).singleOrNull()
            }

            // then: the result should be null
            assert(record == null)

        }

    }

    @Test
    fun `should throw NoSuchRecordException when singleOrNull() is called with multiple records`() = runTest {

        // given: multiple people in the database
        driver.populate("""
            CREATE (p1:Person {name: 'David', age: 28})
            CREATE (p2:Person {name: 'Emma', age: 32})
            CREATE (p3:Person {name: 'Frank', age: 45})
        """.trimIndent())

        driver.coroutineSession().use { session ->

            // when
            val error = assertFailsWith<NoSuchRecordException> {
                session.executeRead { tx ->
                    tx.run(
                        "MATCH (p:Person) RETURN p ORDER BY p.name"
                    ).singleOrNull()
                }
            }

            // then
            assert(error.message == "Expected at most 1 record but found at least 2")
        }

    }

    @Test
    fun `should throw ResultConsumedException when singleOrNull() is called after consume()`() = runTest {

        // given: data in the database
        driver.populate("CREATE (p:Person {name: 'ConsumedPerson', age: 38})")

        driver.coroutineSession().use { session ->

            // when: getting a result, consuming it first, then trying to call singleOrNull()
            val result = session.run(
                "MATCH (p:Person) WHERE p.name = 'ConsumedPerson' RETURN p"
            )

            // Consume the result first
            result.consume()

            // then: attempting to call singleOrNull() should throw ResultConsumedException
            assertFailsWith<ResultConsumedException> {
                result.singleOrNull()
            }

        }

    }

    @Test
    fun `should throw ResultConsumedException when singleOrNull() is called after records() collection`() = runTest {

        // given: data in the database
        driver.populate("CREATE (p:Person {name: 'CollectedPerson', age: 55})")

        driver.coroutineSession().use { session ->

            // when: getting a result, collecting records first, then trying to call singleOrNull()
            val result = session.run(
                "MATCH (p:Person) WHERE p.name = 'CollectedPerson' RETURN p"
            )

            // Collect records first
            result.records().collect()

            // then: attempting to call singleOrNull() should throw ResultConsumedException
            assertFailsWith<ResultConsumedException> {
                result.singleOrNull()
            }

        }

    }

    @Test
    fun `should accept database parameter in session configuration`() = runTest {
        // when: creating a session with database parameter
        // Note: Neo4j harness uses the default database regardless of the parameter
        driver.coroutineSession {
            database = "neo4j"
        }.use { session ->
            session.run("CREATE (p:Person {name: 'Test User'})")
        }

        // then: data should be accessible
        val count = driver.coroutineSession {
            database = "neo4j"
        }.use { session ->
            session.run("MATCH (p:Person) RETURN count(p) AS count")
                .records()
                .map { it["count"].asInt() }
                .first()
        }
        assert(count == 1)
    }

    @Test
    fun `should accept transaction configuration using builder`() = runTest {
        // when: creating a transaction with configuration
        driver.coroutineSession().use { session ->

            val tx = session.beginTransaction {
                timeout = 30.seconds
                metadata = mapOf("app" to "test", "user" to "tester")
            }

            // then: transaction should work with the configuration
            tx.run("CREATE (p:Person {name: 'Configured User'})").consume()
            tx.commit()
            tx.close()
        }

        // verify: data should be accessible
        val count = driver.coroutineSession().use { session ->
            session.run("MATCH (p:Person) WHERE p.name = 'Configured User' RETURN count(p) AS count")
                .records()
                .map { it["count"].asInt() }
                .first()
        }
        assert(count == 1)
    }

    @Test
    fun `should accept transaction configuration in executeWrite`() = runTest {
        // when: using executeWrite with transaction configuration
        val summary = driver.coroutineSession().use { session ->
            session.executeWrite({
                timeout = 30.seconds
                metadata = mapOf("app" to "test", "operation" to "write")
            }) { tx ->
                tx.run("CREATE (p:Person {name: 'Managed Transaction User'})").consume()
            }
        }

        // then: transaction should complete successfully
        summary should {
            have(queryType() == QueryType.WRITE_ONLY)
            counters() should {
                have(containsUpdates())
                have(nodesCreated() == 1)
            }
        }

        // verify: data should be accessible
        val count = driver.coroutineSession().use { session ->
            session.run("MATCH (p:Person) WHERE p.name = 'Managed Transaction User' RETURN count(p) AS count")
                .records()
                .map { it["count"].asInt() }
                .first()
        }
        assert(count == 1)
    }

    // performance tests
    @Test
    fun `should stream big amount of data with minimal impact`() = runTest {
        val duration = measureTime {
            val (last, summary) = driver.coroutineSession().use { session ->
                session.executeRead { tx ->
                    val result = tx.run("UNWIND range(1, 1000000) AS n RETURN n")
                    val last = result.records().last()["n"].asInt()
                    last to result.consume()
                }
            }
            assert(last == 1000000)
            println("Result available after: ${summary.resultAvailableAfter()}")
            println("Result consumed after: ${summary.resultConsumedAfter()}")
        }
        println("Processed in $duration")
    }

    @Test
    fun `should stream big amount of data with sync api`() {
        val duration = measureTime {
            val (last, summary) = driver.session().use { session ->
                session.executeRead { tx ->
                    val result = tx.run("UNWIND range(1, 1000000) AS n RETURN n")
                    val last = result.stream().reduce { _, second -> second }.get()["n"].asInt()
                    last to result.consume()
                }
            }
            assert(last == 1000000)
            println("Result available after: ${summary.resultAvailableAfter()} ms")
            println("Result consumed after: ${summary.resultConsumedAfter()} ms")
        }
        println("Processed in $duration")
    }

    @Test
    fun `should handle multiple concurrent queries efficiently`() = runTest {
        val duration = measureTime {
            List(100) { i ->
                async(Dispatchers.IO) {
                    driver.coroutineSession().use { session ->
                        session.executeRead { tx ->
                            tx.run("UNWIND range(1, 10000) AS n RETURN n * $i AS result")
                                .records()
                                .count()
                        }
                    }
                }
            }.awaitAll()
        }
        println("Processed in $duration")
    }

    @Test
    fun `should handle multiple concurrent queries less efficiently - sync version`() {
        val executor = Executors.newFixedThreadPool(100)
        try {
            val duration = measureTime {
                val futures = List(100) { i ->
                    executor.submit<Int> {
                        driver.session().use { session ->
                            session.executeRead { tx ->
                                tx.run("UNWIND range(1, 30000) AS n RETURN n * $i AS result")
                                    .stream()
                                    .count()
                                    .toInt()
                            }
                        }
                    }
                }
                futures.map { it.get() } // Wait for all to complete
            }
            println("Processed in $duration")
        } finally {
            executor.shutdown()
            executor.awaitTermination(1, TimeUnit.MINUTES)
        }
    }

    @Test
    fun `should perform write and read using driver shortcuts with default configs`() = runTest {
        // given: write using driver.write shortcut
        driver.write { tx ->
            tx.run(
                $$"""
                    CREATE (a:Person {name: $name, age: $age})
                """.trimIndent(),
                mapOf(
                    "name" to "Bob",
                    "age" to 42
                )
            )
        }

        // when: read using driver.read shortcut
        val (name, age) = driver.read { tx ->
            val records = tx.run(
                "MATCH (p:Person {name: 'Bob'}) RETURN p.name AS name, p.age AS age"
            ).records().toList()

            records.first().let {
                it["name"].asString() to it["age"].asInt()
            }
        }

        // then
        assert(name == "Bob")
        assert(age == 42)
    }

    @Test
    fun `should perform write and read using driver shortcuts with explicit configs`() = runTest {
        // given: write using driver.write shortcut with explicit TransactionConfig
        val txConfig = TransactionConfig {
            timeout = 5.seconds
        }

        driver.write(transactionConfig = txConfig) { tx ->
            tx.run("CREATE (a:Person {name: 'Charlie', age: 30})")
        }

        // when: read using driver.read shortcut with explicit TransactionConfig
        val name = driver.read(transactionConfig = txConfig) { tx ->
            tx.run(
                "MATCH (p:Person {name: 'Charlie'}) RETURN p.name AS name"
            ).records().map { it["name"].asString() }.first()
        }

        assert(name == "Charlie")
    }

    @Test
    fun `should populate database using populate shortcut`() = runTest {
        // when: using populate to insert test data
        driver.populate(
            """
                CREATE (p1:Person {name: 'Dave', age: 35})
                CREATE (p2:Person {name: 'Eve', age: 28})
                CREATE (p1)-[:KNOWS]->(p2)
            """.trimIndent()
        )

        // then: data should be accessible
        val count = driver.read { tx ->
            tx.run("MATCH (p:Person) RETURN count(p) AS count")
                .single()["count"].asInt()
        }

        assert(count == 2)

        // and: relationships should exist
        val relationshipCount = driver.read { tx ->
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
        driver.populate(
            query = "CREATE (p:Person {name: 'Frank', age: 40})",
            transactionConfig = txConfig
        )

        // then: data should be accessible
        val name = driver.read { tx ->
            tx.run("MATCH (p:Person {name: 'Frank'}) RETURN p.name AS name")
                .single()["name"].asString()
        }

        assert(name == "Frank")
    }

}
