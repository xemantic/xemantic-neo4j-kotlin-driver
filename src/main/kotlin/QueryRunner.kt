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

import org.intellij.lang.annotations.Language
import org.neo4j.driver.Query
import org.neo4j.driver.Record
import org.neo4j.driver.Value

/**
 * Common interface for components that can execute Neo4j queries using Coroutines API.
 *
 * **Note:** it mimics [org.neo4j.driver.async.AsyncQueryRunner] by adapting it to
 * Kotlin-idiomatic coroutine conventions. All [run] functions are
 * `suspend` and returning a coroutine-friendly [Result] instead of
 * [java.util.concurrent.CompletionStage] of [org.neo4j.driver.async.ResultCursor].
 *
 * ## Important notes on semantics
 *
 * Queries run in the same [QueryRunner] are guaranteed
 * to execute in order, meaning changes made by one query will be seen
 * by all subsequent queries in the same [QueryRunner].
 *
 * However, to allow handling very large results, and to improve performance,
 * result streams are retrieved lazily from the network. This means that when
 * [run] methods return a result, the query has only started executing - it may not
 * have completed yet. Most of the time, you will not notice this, because the
 * driver automatically waits for queries to complete at specific points to
 * fulfill its contracts.
 *
 * Specifically, the driver will ensure all outstanding queries are completed
 * whenever you:
 *
 * - Read from or discard a result, for instance by collecting the
 *   [Result.records] Flow or calling [Result.consume]
 * - Explicitly commit/rollback a transaction using [Transaction.commit], [Transaction.rollback]
 * - Close a session using [Session.close]
 *
 * As noted, most of the time, you will not need to consider this - your writes will
 * always be durably stored as long as you either use the results, explicitly commit
 * [Transaction]s or close the session you used, using [Session.close].
 *
 * While these semantics introduce some complexity, it gives the driver the ability
 * to handle infinite result streams (like subscribing to events), significantly lowers
 * the memory overhead for your application and improves performance.
 *
 * ## Asynchronous API
 *
 * All overloads of [QueryRunner.run] execute queries in async fashion and therefore are defined
 * as `suspend` functions returning [Result] from which a Flow of [Record]s, represented as a function,
 * can be obtained.
 * a new {@link ResultCursor}. Stage can be completed exceptionally when error happens, e.g. connection can't
 * be acquired from the pool.
 *
 * @see org.neo4j.driver.async.AsyncQueryRunner
 * @see Session
 * @see Transaction
 */
public interface QueryRunner {

    /**
     * Run a query and return a [Result].
     *
     * This method takes a set of parameters that will be injected into the query by Neo4j.
     * Using parameters is highly encouraged, it helps avoid dangerous cypher injection attacks and improves database
     * performance as Neo4j can re-use query plans more often.
     *
     * This particular method takes a [Value] as its input. This is useful if you want to take a map-like value that
     * you've gotten from a prior result and send it back as parameters.
     *
     * If you are creating parameters programmatically, other overloaded variants of [run] function might be more
     * helpful.
     *
     * ```
     * val result = session.run(
     *     query = $$"MATCH (n) WHERE n.name = $myNameParam RETURN (n)",
     *     parameters = Values.parameters("myNameParam", "Bob")
     * );
     * ```
     *
     * @param query      text of a Neo4j query
     * @param parameters input parameters, should be a map Value, see [org.neo4j.driver.Values.parameters].
     * @return a result.
     *
     * @see org.neo4j.driver.async.AsyncQueryRunner.runAsync
     */
    public suspend fun run(
        @Language("cypher")
        query: String,
        parameters: Value
    ): Result

    /**
     * Run a query and return a [Result].
     *
     * This method takes a set of parameters that will be injected into the query by Neo4j.
     * Using parameters is highly encouraged, it helps avoid dangerous
     * cypher injection attacks and improves database performance as Neo4j can re-use query plans more often.
     *
     * This version of run takes a [Map] of parameters.
     * The values in the map must be values that can be converted to Neo4j types.
     * See [org.neo4j.driver.Values.parameters] for a list of allowed types.
     *
     * ```
     * val result = session.run(
     *     query = $$"MATCH (n) WHERE n.name = $myNameParam RETURN (n)",
     *     parameters = mapOf("myNameParam" to "Bob")
     * );
     * ```
     *
     * @param query      text of a Neo4j query
     * @param parameters input data for the query
     * @return a result.
     *
     * @see org.neo4j.driver.async.AsyncQueryRunner.runAsync
     */
    public suspend fun run(
        @Language("cypher")
        query: String,
        parameters: Map<String, Any?>
    ): Result

    /**
     * Run a query and return a [Result].
     *
     * This method takes a set of parameters that will be injected into the query by Neo4j.
     * Using parameters is highly encouraged, it helps avoid dangerous
     * cypher injection attacks and improves database performance as Neo4j can re-use query plans more often.
     *
     * This version of run takes a [Record] of parameters,
     * which can be useful if you want to use the output of one query as input for another.
     *
     * @param query      text of a Neo4j query
     * @param parameters input data for the query
     * @return a result.
     *
     * @see org.neo4j.driver.async.AsyncQueryRunner.runAsync
     */
    public suspend fun run(
        @Language("cypher")
        query: String,
        parameters: Record
    ): Result

    /**
     * Run a query and return a [Result].
     *
     * @param query text of a Neo4j query
     * @return a result
     *
     * @see org.neo4j.driver.async.AsyncQueryRunner.runAsync
     */
    public suspend fun run(
        @Language("cypher")
        query: String
    ): Result

    /**
     * Run a query and return a [Result].
     *
     * @param query a Neo4j query
     * @return a result.
     *
     * @see org.neo4j.driver.async.AsyncQueryRunner.runAsync
     */
    public suspend fun run(query: Query): Result

}
