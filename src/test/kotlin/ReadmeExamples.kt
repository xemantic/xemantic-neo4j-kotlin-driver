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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.neo4j.driver.Driver
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import com.xemantic.kotlin.core.use

class ReadmeExample {

    private lateinit var driver: Driver

    fun `README example`() = runTest {

        val neo4j = DispatchedNeo4jOperations(
            driver = driver,
            dispatcher = Dispatchers.IO.limitedParallelism(90),
            defaultSessionConfig = {
                database = "people"
            },
            defaultTransactionConfig = {
                timeout = 30.seconds
            }
        )

        val peopleCount = neo4j.read { tx ->
            tx.run("MATCH (p:Person) RETURN count(p)").single().get(0).asInt()
        }

        val summary = neo4j.write { tx ->
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
        println(summary)

        // This driver can also automatically map properties
        // between neo4j records and Kotlin classes
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

        val person = Person(
            name = "Alice Johnson",
            email = "alice.johnson@email.com",
            age = 28,
            city = "New York",
            skills = listOf("Python", "JavaScript", "SQL"),
            active = true
        )

        // Write to the database



        val createdPerson = neo4j.write { tx ->
            tx.run(
                query = $$"""
                    CREATE (person:Person $props)
                    SET createdAt: datetime()
                    RETURN person
                """,
                parameters = mapOf(
                    "props" to person.toProperties()
                )
            ).single {
                it["person"].toObject<Person>()
            }
        }
        assert(createdPerson.createdAt > person.createdAt)

        // Read from the database with altered session config
        val storedPerson = neo4j.read(
            SessionConfig {
                database = "neo4j"
            }
        ) { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p"
            ).single {
                it["p"].toObject<Person>()
            }
        }

        println(storedPerson)

        // the flow() function returns standard Kotlin Flow<Person>
        neo4j.flow { tx ->
            tx.run("MATCH (p:Person) RETURN p").records() // it is a Flow<Person> already
            // but this time we are returning it outside of the transaction scope
        }.collect {
            println(it)
        }
        // the Neo4j transaction/session will be closed once the flow is collected

        neo4j.withSession { session ->
            session.beginTransaction {
                timeout = 10.minutes
            }.use { tx ->
                try {
                    tx.run {
                    }
                } catch (e: Exception) {
                    tx.rollback()
                }
            }

            session.executeRead { tx ->
                tx.run("").single { it["p"] }
            }

            session.executeWrite { tx ->
                tx.run("")
            }
        }

    }

}
