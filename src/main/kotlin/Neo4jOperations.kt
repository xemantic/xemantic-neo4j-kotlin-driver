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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.neo4j.driver.Driver
import org.neo4j.driver.Query
import org.neo4j.driver.Record
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.TransactionConfig
import com.xemantic.kotlin.core.use

/**
 * Provides high-level operations for interacting with Neo4j database.
 *
 * This interface abstracts common Neo4j operations, providing convenience methods for read and write
 * transactions with automatic session lifecycle management. It's designed to simplify the most common
 * use cases while still allowing for flexible configuration.
 *
 * Implementations should ensure proper resource cleanup and handle coroutine context appropriately.
 *
 * @see DispatchedNeo4jOperations
 */
public interface Neo4jOperations {

    /**
     * Default configuration to use for sessions created by this operations instance.
     */
    public val defaultSessionConfig: SessionConfig

    /**
     * Default configuration to use for transactions created by this operations instance.
     */
    public val defaultTransactionConfig: TransactionConfig

    /**
     * Execute a block of code with a managed session.
     *
     * This function creates a new session with the specified configuration, executes the provided
     * block, and automatically closes the session when the block completes or throws an exception.
     *
     * This is useful when you need to perform multiple transactions on the same session or need
     * finer control over session lifecycle than what the convenience methods provide.
     *
     * Sample usage:
     *
     * ```
     * neo4j.withSession { session ->
     *     session.executeWrite { tx ->
     *         tx.run("CREATE (p:Person {name: 'Alice'})")
     *     }
     *     session.executeRead { tx ->
     *         tx.run("MATCH (p:Person) RETURN count(p) as count").single {
     *             it["count"].asInt()
     *         }
     *     }
     * }
     * ```
     *
     * @param config configuration for the session.
     * @param block the suspend callback to execute with the session.
     * @param T the return type of the given block.
     * @return the result of the block execution.
     */
    public suspend fun <T> withSession(
        config: SessionConfig = defaultSessionConfig,
        block: suspend (Session) -> T
    ): T

    /**
     * Execute a unit of work as a single, managed read transaction with automatic session lifecycle management.
     *
     * This is a convenience function that creates a session, executes a read transaction, and automatically
     * closes the session when complete. It's ideal for simple read operations that don't require multiple
     * transactions on the same session.
     *
     * The transaction allows for one or more statements to be run within the provided callback.
     * The driver will automatically commit the transaction when the callback completes successfully,
     * or rollback if an exception is thrown.
     *
     * Sample usage:
     *
     * ```
     * val result = neo4j.read { tx ->
     *     tx.run("MATCH (n:Person) RETURN n").records().toList()
     * }
     * ```
     *
     * @param sessionConfig configuration for the session.
     * @param transactionConfig configuration for the transaction.
     * @param block the suspend callback representing the unit of work.
     * @param T the return type of the given unit of work.
     * @return the result of the unit of work.
     *
     * @see Session.executeRead
     */
    public suspend fun <T> read(
        sessionConfig: SessionConfig = defaultSessionConfig,
        transactionConfig: TransactionConfig = defaultTransactionConfig,
        block: suspend (TransactionContext) -> T
    ): T

    /**
     * Execute a unit of work as a single, managed write transaction with automatic session lifecycle management.
     *
     * This is a convenience function that creates a session, executes a write transaction, and automatically
     * closes the session when complete. It's ideal for simple write operations that don't require multiple
     * transactions on the same session.
     *
     * The transaction allows for one or more statements to be run within the provided callback.
     * The driver will automatically commit the transaction when the callback completes successfully,
     * or rollback if an exception is thrown.
     *
     * Sample usage:
     *
     * ```
     * neo4j.write { tx ->
     *     tx.run("CREATE (n:Person {name: 'Alice'})")
     * }
     * ```
     *
     * @param sessionConfig configuration for the session.
     * @param transactionConfig configuration for the transaction.
     * @param block the suspend callback representing the unit of work.
     * @param T the return type of the given unit of work.
     * @return the result of the unit of work.
     *
     * @see Session.executeWrite
     */
    public suspend fun <T> write(
        sessionConfig: SessionConfig = defaultSessionConfig,
        transactionConfig: TransactionConfig = defaultTransactionConfig,
        block: suspend (TransactionContext) -> T
    ): T

    /**
     * Create a Flow with full session lifecycle control.
     *
     * This function provides advanced control over session management by giving the user direct access
     * to the Session object within the Flow block. Unlike the simpler query-based [flow] variants that
     * automatically handle read transactions, this function allows you to manage transactions yourself,
     * mix read and write operations, or perform complex session-level operations.
     *
     * The session is created at the start of Flow collection and automatically closed when the Flow
     * completes or fails, ensuring proper resource cleanup.
     *
     * This is useful when you need to:
     * - Execute multiple transactions within the same session
     * - Mix read and write operations in a streaming context
     * - Have fine-grained control over transaction boundaries
     * - Access session-level operations beyond simple query execution
     * - Process large result sets without loading everything into memory
     *
     * Basic usage with [Session.flow] convenience method:
     *
     * ```
     * neo4j.flow { session ->
     *     session.flow("MATCH (n:Person) RETURN n")
     * }.collect { record ->
     *     println(record["n"])
     * }
     * ```
     *
     * Advanced usage with write operation followed by streaming results:
     *
     * ```
     * neo4j.flow { session ->
     *     // First: perform a write operation
     *     session.executeWrite { tx ->
     *         tx.run("CREATE (n:Log {processed: true})")
     *     }
     *     // Then: stream results using session.flow()
     *     session.flow("MATCH (n:Person) RETURN n")
     * }.collect { record ->
     *     println(record["n"])
     * }
     * ```
     *
     * @param sessionConfig configuration for the session.
     * @param block the suspend callback that receives a Session and produces a Flow.
     * @param T the type of elements emitted by the Flow.
     * @return a Flow that emits elements from the session block.
     *
     * @see withSession
     * @see Session.flow
     * @see Session.executeRead
     * @see Session.executeWrite
     */
    public fun <T> flow(
        sessionConfig: SessionConfig = defaultSessionConfig,
        block: suspend (Session) -> Flow<T>
    ): Flow<T>

    /**
     * Create a Flow that executes a query in a read transaction and streams the resulting records.
     *
     * This is a convenience function that automatically executes a query and returns its records as a Flow.
     * The session and transaction are automatically managed, and resources are cleaned up after the Flow completes.
     *
     * Sample usage:
     *
     * ```
     * neo4j.flow(Query("MATCH (n:Person) WHERE n.age > $age RETURN n", mapOf("age" to 25)))
     *     .collect { record ->
     *         println(record["n"])
     *     }
     * ```
     *
     * @param query the Neo4j query to execute.
     * @param sessionConfig configuration for the session.
     * @param transactionConfig configuration for the transaction.
     * @return a Flow that emits Record elements from the query result.
     *
     * @see flow
     * @see read
     */
    public fun flow(
        query: Query,
        sessionConfig: SessionConfig = defaultSessionConfig,
        transactionConfig: TransactionConfig = defaultTransactionConfig
    ): Flow<Record>

    /**
     * Create a Flow that executes a query in a read transaction and streams the resulting records.
     *
     * This is a convenience function that automatically executes a query with parameters and returns
     * its records as a Flow. The session and transaction are automatically managed, and resources are
     * cleaned up after the Flow completes.
     *
     * Sample usage:
     *
     * ```
     * neo4j.flow(
     *     query = $$"MATCH (n:Person) WHERE n.age > $age RETURN n",
     *     parameters = mapOf("age" to 25)
     * ).collect {
     *     println(it["n"])
     * }
     * ```
     *
     * @param query the Cypher query string to execute.
     * @param parameters input parameters for the query.
     * @param sessionConfig configuration for the session.
     * @param transactionConfig configuration for the transaction.
     * @return a Flow that emits Record elements from the query result.
     *
     * @see flow
     * @see read
     */
    public fun flow(
        @Language("cypher") query: String,
        parameters: Map<String, Any?>,
        sessionConfig: SessionConfig = defaultSessionConfig,
        transactionConfig: TransactionConfig = defaultTransactionConfig
    ): Flow<Record> = flow(
        Query(query, parameters),
        sessionConfig,
        transactionConfig
    )

    /**
     * Create a Flow that executes a query in a read transaction and streams the resulting records.
     *
     * This is a convenience function for queries without parameters. The session and transaction are
     * automatically managed, and resources are cleaned up after the Flow completes.
     *
     * Sample usage:
     *
     * ```
     * neo4j.flow(
     *     "MATCH (n:Person) RETURN n"
     * ).collect { record ->
     *     println(record["n"])
     * }
     * ```
     *
     * @param query the Cypher query string to execute.
     * @param sessionConfig configuration for the session.
     * @param transactionConfig configuration for the transaction.
     * @return a Flow that emits Record elements from the query result.
     *
     * @see flow
     * @see read
     */
    public fun flow(
        @Language("cypher") query: String,
        sessionConfig: SessionConfig = defaultSessionConfig,
        transactionConfig: TransactionConfig = defaultTransactionConfig
    ): Flow<Record> = flow(
        Query(query),
        sessionConfig,
        transactionConfig
    )

    /**
     * Populates the database with data using the provided Cypher query.
     *
     * This is a convenience function that creates a session, executes a write transaction with the provided query,
     * and automatically closes the session when complete. It's particularly useful in tests and utilities
     * for quickly setting up database state without boilerplate session management.
     *
     * Sample usage:
     *
     * ```
     * neo4j.populate("""
     *     CREATE (p1:Person {name: 'Alice', age: 30})
     *     CREATE (p2:Person {name: 'Bob', age: 25})
     * """.trimIndent())
     * ```
     *
     * @param query The Cypher query to execute for populating data
     * @param sessionConfig configuration for the session.
     * @param transactionConfig configuration for the transaction.
     *
     * @see write
     */
    public suspend fun populate(
        @Language("cypher") query: String,
        sessionConfig: SessionConfig = defaultSessionConfig,
        transactionConfig: TransactionConfig = defaultTransactionConfig
    )

}

/**
 * Implementation of [Neo4jOperations] that dispatches all database operations to a specific coroutine dispatcher.
 *
 * This class wraps a Neo4j driver and ensures all database I/O operations are executed on the specified
 * dispatcher, preventing blocking of the calling coroutine context. This is essential for proper
 * integration with structured concurrency and avoiding thread pool exhaustion.
 *
 * @param driver the Neo4j driver instance to use for database operations.
 * @param dispatcher the coroutine dispatcher on which to execute all database operations.
 * @param defaultSessionConfig default configuration for sessions (defaults to Neo4j's default config).
 * @param defaultTransactionConfig default configuration for transactions (defaults to empty config).
 *
 * @see Neo4jOperations
 */
public class DispatchedNeo4jOperations(
    private val driver: Driver,
    private val dispatcher: CoroutineDispatcher,
    override val defaultSessionConfig: SessionConfig = SessionConfig.defaultConfig(),
    override val defaultTransactionConfig: TransactionConfig = TransactionConfig.empty()
) : Neo4jOperations {

    /**
     * Alternative constructor that accepts builder lambdas for configuration.
     *
     * This constructor provides a more Kotlin-idiomatic way to configure sessions and transactions
     * using DSL-style builder blocks.
     *
     * @param driver the Neo4j driver instance to use for database operations.
     * @param dispatcher the coroutine dispatcher on which to execute all database operations.
     * @param defaultSessionConfig builder lambda for session configuration.
     * @param defaultTransactionConfig builder lambda for transaction configuration.
     */
    public constructor(
        driver: Driver,
        dispatcher: CoroutineDispatcher,
        defaultSessionConfig: SessionConfigBuilder.() -> Unit,
        defaultTransactionConfig: TransactionConfigBuilder.() -> Unit
    ) : this(
        driver = driver,
        dispatcher = dispatcher,
        defaultSessionConfig = SessionConfigBuilder().also(
            defaultSessionConfig
        ).build(),
        defaultTransactionConfig = TransactionConfigBuilder().also(
            defaultTransactionConfig
        ).build()
    )

    override suspend fun <T> withSession(
        config: SessionConfig,
        block: suspend (Session) -> T
    ): T = withContext(dispatcher) {
        driver.coroutineSession(config).use { session ->
            block(session)
        }
    }

    override suspend fun <T> write(
        sessionConfig: SessionConfig,
        transactionConfig: TransactionConfig,
        block: suspend (TransactionContext) -> T
    ): T = withSession(sessionConfig) { session ->
        session.executeWrite(transactionConfig, block)
    }

    override suspend fun <T> read(
        sessionConfig: SessionConfig,
        transactionConfig: TransactionConfig,
        block: suspend (TransactionContext) -> T
    ): T = withSession(sessionConfig) { session ->
        session.executeRead(transactionConfig, block)
    }

    override fun <T> flow(
        sessionConfig: SessionConfig,
        block: suspend (Session) -> Flow<T>
    ): Flow<T> = channelFlow {
        withSession(sessionConfig) { session ->
            block(session).collect {
                send(it)
            }
        }
    }

    override fun flow(
        query: Query,
        sessionConfig: SessionConfig,
        transactionConfig: TransactionConfig
    ): Flow<Record> = channelFlow {
        read(sessionConfig, transactionConfig) { tx ->
            tx.run(query).records().collect {
                send(it)
            }
        }
    }

    override suspend fun populate(
        @Language("cypher") query: String,
        sessionConfig: SessionConfig,
        transactionConfig: TransactionConfig
    ) {
        write(sessionConfig, transactionConfig) { tx ->
            tx.run(query)
        }
    }

}
