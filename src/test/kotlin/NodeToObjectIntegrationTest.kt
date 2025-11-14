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
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Integration tests for [toObject] extension function.
 * These tests verify conversion from Neo4j nodes to Kotlin objects.
 */
class NodeToObjectIntegrationTest {

    private val neo4j = DispatchedNeo4jOperations(
        driver = TestNeo4j.driver,
        dispatcher = Dispatchers.IO.limitedParallelism(90)
    )

    @AfterEach
    fun cleanDatabase() {
        TestNeo4j.cleanDatabase()
    }

    @Test
    fun `Node toObject should convert node with primitive types to object`() = runTest {
        // given
        neo4j.populate(
            "CREATE (:Person {name: 'Alice', age: 30})"
        )

        // when
        val person = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p"
            ).single()["p"].toObject<SimplePerson>()
        }

        // then
        person should {
            have(name == "Alice")
            have(age == 30)
        }
    }

    @Test
    fun `Node toObject should handle nullable fields with null values`() = runTest {
        // given
        neo4j.populate(
            "CREATE (:Person {name: 'Bob'})"
        )

        // when
        val person = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p"
            ).single()["p"].toObject<PersonWithNullables>()
        }

        // then
        person should {
            have(name == "Bob")
            have(age == null)
            have(email == null)
        }
    }

    @Test
    fun `Node toObject should handle nullable fields with non-null values`() = runTest {
        // given
        neo4j.populate($$"""
            CREATE (:Person {name: 'Charlie', age: 25, email: 'charlie@example.com'})
        """.trimIndent())

        // when
        val person = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p"
            ).single()["p"].toObject<PersonWithNullables>()
        }

        // then
        person should {
            have(name == "Charlie")
            have(age == 25)
            have(email == "charlie@example.com")
        }
    }

    @Test
    fun `Node toObject should handle all primitive types`() = runTest {
        // given
        neo4j.populate("""
            CREATE (:Person {
                name: 'David',
                age: 35,
                height: 180.5,
                isActive: true,
                salary: 75000,
                birthDate: datetime('1990-05-15T00:00:00Z')
            })
        """.trimIndent())

        // when
        val person = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p"
            ).single()["p"].toObject<PersonWithAllTypes>()
        }

        // then
        person should {
            have(name == "David")
            have(age == 35)
            have(height == 180.5)
            have(isActive)
            have(salary == 75000L)
            have(birthDate == Instant.parse("1990-05-15T00:00:00Z"))
        }
    }

    @Test
    fun `Node toObject should handle nested objects from map projection`() = runTest {
        // given - Store properties flat in Neo4j
        neo4j.populate("""
            CREATE (:Person {
                name: 'Eve',
                age: 28,
                street: '123 Main St',
                city: 'Springfield',
                zipCode: '12345'
            })
        """.trimIndent())

        // when - Use map projection to create nested structure in query result
        val person = neo4j.read { tx ->
            tx.run("""
                MATCH (p:Person)
                RETURN p.name AS name, p.age AS age,
                       {street: p.street, city: p.city, zipCode: p.zipCode} AS address
            """.trimIndent()).single().toObject<PersonWithAddress>()
        }

        // then
        person should {
            have(name == "Eve")
            have(age == 28)
            address should {
                have(street == "123 Main St")
                have(city == "Springfield")
                have(zipCode == "12345")
            }
        }
    }

    @Test
    fun `Node toObject should handle null nested objects`() = runTest {
        // given
        neo4j.populate(
            "CREATE (:Person {name: 'Frank', age: 40})"
        )

        // when
        val person = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p"
            ).single()["p"].toObject<PersonWithOptionalAddress>()
        }

        // then
        person should {
            have(name == "Frank")
            have(age == 40)
            have(address == null)
        }
    }

    @Test
    fun `Node toObject should convert arrays to lists`() = runTest {
        // given
        neo4j.populate("""
            CREATE (:Person {
                name: 'Grace',
                tags: ['developer', 'architect', 'speaker']
            })
        """.trimIndent())

        // when
        val person = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p"
            ).single()["p"].toObject<PersonWithList>()
        }

        // then
        person should {
            have(name == "Grace")
            have(tags == listOf("developer", "architect", "speaker"))
        }
    }

    @Test
    fun `Node toObject should convert arrays to sets`() = runTest {
        // given
        neo4j.populate("""
            CREATE (:Person {
                name: 'Hank',
                skills: ['Kotlin', 'Java', 'Python']
            })
        """.trimIndent())

        // when
        val person = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p"
            ).single()["p"].toObject<PersonWithSet>()
        }

        // then
        person should {
            have(name == "Hank")
            have(skills == setOf("Kotlin", "Java", "Python"))
        }
    }

    @Test
    fun `Node toObject should handle arrays of numbers`() = runTest {
        // given
        neo4j.populate("""
            CREATE (:Person {
                name: 'Ivy',
                scores: [95, 87, 92, 88]
            })
        """.trimIndent())

        // when
        val person = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p"
            ).single()["p"].toObject<PersonWithNumbers>()
        }

        // then
        person should {
            have(name == "Ivy")
            have(scores == listOf(95, 87, 92, 88))
        }
    }

    @Test
    fun `Node toObject should convert string to enum`() = runTest {
        // given
        neo4j.populate(
            "CREATE (:Person {name: 'Jack', role: 'ADMIN'})"
        )

        // when
        val person = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p"
            ).single()["p"].toObject<PersonWithEnum>()
        }

        // then
        person should {
            have(name == "Jack")
            have(role == Role.ADMIN)
        }
    }

    @Test
    fun `Node toObject should handle empty arrays`() = runTest {
        // given
        neo4j.populate(
            "CREATE (:Person {name: 'Leo', tags: []})"
        )

        // when
        val person = neo4j.read { tx ->
            tx.run(
                "MATCH (p:Person) RETURN p"
            ).single()["p"].toObject<PersonWithList>()
        }

        // then
        person should {
            have(name == "Leo")
            have(tags.isEmpty())
        }
    }

}
