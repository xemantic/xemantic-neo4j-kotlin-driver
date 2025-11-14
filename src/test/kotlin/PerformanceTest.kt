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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.measureTime
import kotlin.use

class PerformanceTest {

    private val driver = TestNeo4j.driver

    private val neo4j = DispatchedNeo4jOperations(
        driver = TestNeo4j.driver,
        dispatcher = Dispatchers.IO.limitedParallelism(100)
    )

    @AfterEach
    fun cleanUp() {
        TestNeo4j.cleanDatabase()
    }

    @Test
    fun `should stream big amount of data with minimal impact`() = runTest {
        val duration = measureTime {
            val (last, summary) = neo4j.read { tx ->
                val result = tx.run("UNWIND range(1, 1000000) AS n RETURN n")
                val last = result.records().last()["n"].asInt()
                last to result.consume()
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
                    neo4j.read { tx ->
                        tx.run("UNWIND range(1, 10000) AS n RETURN n * $i AS result")
                            .records()
                            .count()
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

}
