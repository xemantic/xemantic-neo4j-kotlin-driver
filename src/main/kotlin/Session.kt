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

import com.xemantic.neo4j.driver.internal.InternalSession
import org.intellij.lang.annotations.Language
import org.neo4j.driver.*
import org.neo4j.driver.async.AsyncSession

/**
 * Create a new Coroutine-API session.
 *
 * Sample usage:
 *
 * ```
 * driver.coroutineSession({ // session config
 *     database = "<dbname>"
 * }).use { session ->
 *     // use session
 * }
 * ```
 *
 * @param config session config
 * @return session instance
 *
 * @see Driver.session
 */
public fun Driver.coroutineSession(
    config: SessionConfigBuilder.() -> Unit
): Session = coroutineSession(
    SessionConfigBuilder().also(config).build()
)

/**
 * Create a new Coroutine-API session.
 *
 * Sample usage:
 *
 * ```
 * driver.coroutineSession().use { session ->
 *     // use session
 * }
 * ```
 *
 * @param config session config
 * @return session instance
 *
 * @see Driver.session
 */
public fun Driver.coroutineSession(
    config: SessionConfig = SessionConfig.defaultConfig()
): Session = InternalSession(
    session(AsyncSession::class.java, config)
)

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
 * val result = driver.read { tx ->
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
public suspend fun <T> Driver.read(
    sessionConfig: SessionConfig = SessionConfig.defaultConfig(),
    transactionConfig: TransactionConfig = TransactionConfig.empty(),
    block: suspend (tx: TransactionContext) -> T
): T = coroutineSession(sessionConfig).use { session ->
    session.executeRead(transactionConfig, block)
}

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
 * driver.write { tx ->
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
public suspend fun <T> Driver.write(
    sessionConfig: SessionConfig = SessionConfig.defaultConfig(),
    transactionConfig: TransactionConfig = TransactionConfig.empty(),
    block: suspend (tx: TransactionContext) -> T
): T = coroutineSession(sessionConfig).use { session ->
    session.executeWrite(transactionConfig, block)
}

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
 * driver.populate("""
 *     CREATE (p1:Person {name: 'Alice', age: 30})
 *     CREATE (p2:Person {name: 'Bob', age: 25})
 * """.trimIndent())
 * ```
 *
 * @param query The Cypher query to execute for populating data
 * @param sessionConfig configuration for the session.
 * @param transactionConfig configuration for the transaction.
 *
 * @see Driver.write
 */
public suspend fun Driver.populate(
    @Language("cypher") query: String,
    sessionConfig: SessionConfig = SessionConfig.defaultConfig(),
    transactionConfig: TransactionConfig = TransactionConfig.empty()
) {
    coroutineSession(sessionConfig).use { session ->
        session.executeWrite(transactionConfig) { tx ->
            tx.run(query)
        }
    }
}

/**
 * A coroutine session provides a Kotlin-idiomatic API for interacting with Neo4j using structured concurrency.
 *
 * This interface extends [BaseSession] and [QueryRunner] to provide session management and query execution
 * capabilities using Kotlin coroutines and Flow.
 *
 * @see Result
 * @see Transaction
 */
public interface Session : BaseSession, QueryRunner, SuspendCloseable {

    /**
     * Begin a new *unmanaged [Transaction]* with the specified [TransactionConfig].
     *
     * At most one transaction may exist in a session at any point in time. To maintain multiple concurrent transactions,
     * use multiple concurrent sessions.
     *
     * This is a suspend function that uses structured concurrency without blocking threads.
     *
     * ```
     * driver.coroutineSession().use { session ->
     *     tx = session.beginTransaction()
     *     // use transaction: tx.run(...)
     *     tx.close()
     * }
     * ```
     *
     * @param config configuration for the new transaction.
     * @return a new [Transaction]
     */
    public suspend fun beginTransaction(
        config: TransactionConfig = TransactionConfig.empty()
    ): Transaction

    /**
     * Begin a new *unmanaged [Transaction]* with the [TransactionConfig] provided via the [TransactionConfigBuilder].
     *
     * At most one transaction may exist in a session at any point in time. To maintain multiple concurrent transactions,
     * use multiple concurrent sessions.
     *
     * This is a suspend function that uses structured concurrency without blocking threads.
     *
     * ```
     * driver.coroutineSession {
     *     timeout = 3.seconds
     * }.use { session ->
     *     tx = session.beginTransaction()
     *     // use transaction: tx.run(...)
     *     tx.close()
     * }
     * ```
     *
     * @param config configuration for the new transaction.
     * @return a new [Transaction]
     */
    public suspend fun beginTransaction(
        config: TransactionConfigBuilder.() -> Unit
    ): Transaction = beginTransaction(
        TransactionConfigBuilder().also(config).build()
    )

    /**
     * Execute a unit of work as a single, managed transaction with read access mode and automatic retry behavior.
     *
     * The transaction allows for one or more statements to be run within the provided callback.
     *
     * The driver will attempt to commit the transaction when the provided unit of work completes successfully.
     * Any exception thrown by the unit of work will result in a rollback attempt and abortion of execution,
     * unless the exception is considered retryable by the driver.
     *
     * The provided unit of work should not return a [Result] object as it won't be valid outside the scope of the transaction.
     *
     * This function uses structured concurrency and suspends as needed without blocking threads.
     *
     * If a [org.neo4j.driver.exceptions.RetryableException] is thrown and the driver is in a position to retry,
     * it will call the provided callback again to retry the transaction. Note that the entire transaction is retried,
     * not just individual operations.
     *
     * ```
     * driver.coroutineSession().use { session ->
     *     session.executeRead { tx ->
     *         // use transaction: tx.run(...)
     *     }
     * }
     * ```
     *
     * @param config   configuration for all transactions started to execute the unit of work.
     * @param callback the suspend callback representing the unit of work.
     * @param T        the return type of the given unit of work.
     * @return the result of the unit of work.
     *
     * @see AsyncSession.executeReadAsync
     */
    public suspend fun <T> executeRead(
        config: TransactionConfig = TransactionConfig.empty(),
        callback: suspend (TransactionContext) -> T
    ): T

    /**
     * Execute a unit of work as a single, managed transaction with read access mode and automatic retry behavior.
     *
     * The transaction allows for one or more statements to be run within the provided callback.
     *
     * The driver will attempt to commit the transaction when the provided unit of work completes successfully.
     * Any exception thrown by the unit of work will result in a rollback attempt and abortion of execution,
     * unless the exception is considered retryable by the driver.
     *
     * The provided unit of work should not return a [Result] object as it won't be valid outside the scope of the transaction.
     *
     * This function uses structured concurrency and suspends as needed without blocking threads.
     *
     * If a [org.neo4j.driver.exceptions.RetryableException] is thrown and the driver is in a position to retry,
     * it will call the provided callback again to retry the transaction. Note that the entire transaction is retried,
     * not just individual operations.
     *
     * ```
     * driver.coroutineSession().use { session ->
     *     session.executeRead({
     *         timeout = 5.seconds
     *     }) { tx ->
     *         // use transaction: tx.run(...)
     *     }
     * }
     * ```
     *
     * @param config   configuration for all transactions started to execute the unit of work.
     * @param callback the suspend callback representing the unit of work.
     * @param T        the return type of the given unit of work.
     * @return the result of the unit of work.
     *
     * @see AsyncSession.executeReadAsync
     */
    public suspend fun <T> executeRead(
        config: TransactionConfigBuilder.() -> Unit,
        callback: suspend (TransactionContext) -> T
    ): T = executeRead(
        config = TransactionConfigBuilder().also(config).build(),
        callback
    )

    /**
     * Execute a unit of work as a single, managed transaction with write access mode and automatic retry behavior.
     *
     * The transaction allows for one or more statements to be run within the provided callback.
     *
     * The driver will attempt to commit the transaction when the provided unit of work completes successfully.
     * Any exception thrown by the unit of work will result in a rollback attempt and abortion of execution,
     * unless the exception is considered retryable by the driver.
     *
     * The provided unit of work should not return a [Result] object as it won't be valid outside the scope of the transaction.
     *
     * This function uses structured concurrency and suspends as needed without blocking threads.
     *
     * If a [org.neo4j.driver.exceptions.RetryableException] is thrown and the driver is in a position to retry,
     * it will call the provided callback again to retry the transaction. Note that the entire transaction is retried,
     * not just individual operations.
     *
     * ```
     * driver.coroutineSession().use { session ->
     *     session.executeWrite { tx ->
     *         // use transaction: tx.run(...)
     *     }
     * }
     * ```
     *
     * @param config   configuration for all transactions started to execute the unit of work.
     * @param callback the suspend callback representing the unit of work.
     * @param T        the return type of the given unit of work.
     * @return the result of the unit of work.
     *
     * @see AsyncSession.executeWriteAsync
     */
    public suspend fun <T> executeWrite(
        config: TransactionConfig = TransactionConfig.empty(),
        callback: suspend (TransactionContext) -> T
    ): T

    /**
     * Execute a unit of work as a single, managed transaction with write access mode and automatic retry behavior.
     *
     * The transaction allows for one or more statements to be run within the provided callback.
     *
     * The driver will attempt to commit the transaction when the provided unit of work completes successfully.
     * Any exception thrown by the unit of work will result in a rollback attempt and abortion of execution,
     * unless the exception is considered retryable by the driver.
     *
     * The provided unit of work should not return a [Result] object as it won't be valid outside the scope of the transaction.
     *
     * This function uses structured concurrency and suspends as needed without blocking threads.
     *
     * If a [org.neo4j.driver.exceptions.RetryableException] is thrown and the driver is in a position to retry,
     * it will call the provided callback again to retry the transaction. Note that the entire transaction is retried,
     * not just individual operations.
     *
     * ```
     * driver.coroutineSession().use { session ->
     *     session.executeWrite({
     *         timeout = 5.seconds
     *     }) { tx ->
     *         // use transaction: tx.run(...)
     *     }
     * }
     * ```
     *
     * @param config   configuration for all transactions started to execute the unit of work.
     * @param callback the suspend callback representing the unit of work.
     * @param T        the return type of the given unit of work.
     * @return the result of the unit of work.
     *
     * @see AsyncSession.executeWriteAsync
     */
    public suspend fun <T> executeWrite(
        config: TransactionConfigBuilder.() -> Unit,
        callback: suspend (TransactionContext) -> T
    ): T = executeWrite(
        config = TransactionConfigBuilder().also(config).build(),
        callback = callback
    )

    /**
     * Run a query with parameters in an auto-commit transaction with specified [TransactionConfig] and return a [Result].
     *
     * Invoking this method will result in a Bolt RUN message exchange with the server.
     *
     * This method takes a set of [parameters] that will be injected into the query by Neo4j. Using [parameters] is highly encouraged,
     * it helps avoid dangerous cypher injection attacks and improves database performance as Neo4j can re-use query plans more often.
     *
     * This version of run takes a [Map] of parameters. The values in the map must be values that can be converted to Neo4j types.
     * See [Values.parameters] for a list of allowed types.
     *
     * If parameters are omitted, an empty parameter map is used instead.
     *
     * This version of run allows to configure the transaction as the last lambda expression of the call.
     *
     * Example:
     * ```
     * driver.coroutineSession().use { session ->
     *     session.run(
     *         query = $$"MATCH (n) WHERE n.name = $myNameParam RETURN (n)",
     *         parameters = mapOf("myNameParam" to "Bob")
     *     ) {
     *         timeout = 5.seconds
     *     }.records().firstOrNull()
     * }
     * ```
     *
     * @param query      text of a Neo4j query.
     * @param parameters input data for the query, empty if not specified.
     * @param config     configuration for the new transaction.
     * @return a result.
     *
     * @see AsyncSession.runAsync
     */
    public suspend fun run(
        query: String,
        parameters: Map<String, Any?> = emptyMap(),
        config: TransactionConfigBuilder.() -> Unit
    ): Result = run(
        query = Query(query, parameters),
        config = config
    )

    /**
     * Run a query with parameters in an auto-commit transaction with specified [TransactionConfig] and return a [Result].
     *
     * Invoking this method will result in a Bolt RUN message exchange with the server.
     *
     * This method takes a set of [parameters] that will be injected into the query by Neo4j. Using [parameters] is highly encouraged,
     * it helps avoid dangerous cypher injection attacks and improves database performance as Neo4j can re-use query plans more often.
     *
     * This version of run takes a [Map] of parameters. The values in the map must be values that can be converted to Neo4j types.
     * See [Values.parameters] for a list of allowed types.
     *
     * If parameters are omitted, an empty parameter map is used instead.
     *
     * Example:
     * ```
     * driver.coroutineSession().use { session ->
     *     session.run(
     *         query = $$"MATCH (n) WHERE n.name = $myNameParam RETURN (n)",
     *         parameters = mapOf("myNameParam" to "Bob")
     *     ).records().firstOrNull()
     * }
     * ```
     *
     * @param query      text of a Neo4j query.
     * @param parameters input data for the query, empty if not specified.
     * @param config     configuration for the new transaction, or [TransactionConfig.empty] if not provided.
     * @return a result.
     *
     * @see AsyncSession.runAsync
     */
    public suspend fun run(
        query: String,
        parameters: Map<String, Any?> = emptyMap(),
        config: TransactionConfig = TransactionConfig.empty()
    ): Result = run(Query(query, parameters), config)

    /**
     * Run a query with an auto-commit transaction with specified [TransactionConfig] and return a [Result].
     *
     * Invoking this method will result in a Bolt RUN message exchange with the server.
     *
     * This version of run allows to configure the transaction as the last lambda expression of the call.
     *
     * Example:
     * ```
     * driver.coroutineSession().use { session ->
     *     session.run(
     *         query = Query("MATCH (n) WHERE n.name = 'foo' RETURN (n)")
     *     ) {
     *         timeout = 5.seconds
     *     }.records().firstOrNull()
     * }
     * ```
     *
     * @param query      text of a Neo4j query.
     * @param config     configuration for the new transaction.
     * @return a result.
     *
     * @see AsyncSession.runAsync
     */
    public suspend fun run(
        query: Query,
        config: TransactionConfigBuilder.() -> Unit
    ): Result = run(
        query = query,
        config = TransactionConfigBuilder().also(config).build()
    )

    /**
     * Run a query with an auto-commit transaction with specified [TransactionConfig] and return a [Result].
     *
     * Invoking this method will result in a Bolt RUN message exchange with the server.
     *
     * Example:
     * ```
     * driver.coroutineSession().use { session ->
     *     session.run(
     *         query = Query("MATCH (n) WHERE n.name = 'foo' RETURN (n)")
     *     ).records().firstOrNull()
     * }
     * ```
     *
     * @param query      text of a Neo4j query.
     * @param config     configuration for the new transaction.
     * @return a result.
     *
     * @see AsyncSession.runAsync
     */
    public suspend fun run(
        query: Query,
        config: TransactionConfig
    ): Result

    /**
     * Return a set of last bookmarks.
     *
     * When no new bookmark is received, the initial bookmarks are returned.
     * This may happen when no work has been done using the session.
     * Multivalued Bookmark instances will be mapped to distinct Bookmark instances.
     * If no initial bookmarks have been provided, an empty set is returned.
     *
     * @return the immutable set of last bookmarks.
     *
     * @see AsyncSession.lastBookmarks
     */
    public fun lastBookmarks(): Set<Bookmark>

    /**
     * Close this session and release associated resources.
     *
     * In the default driver usage, closing and accessing sessions is very low cost.
     *
     * This operation is not needed if:
     * 1. All results created in the session have been fully consumed (Flow collected or consume() called), and
     * 2. All transactions opened by this session have been either committed or rolled back.
     *
     * This method is a fallback if you failed to fulfill the two requirements above. This suspend function completes
     * when all outstanding queries in the session have completed, meaning any writes you performed are guaranteed to be
     * durably stored. It might throw an exception when there are unconsumed errors from previous queries or transactions.
     *
     * @see AsyncSession.closeAsync
     */
    public override suspend fun close()

}

/**
 * Builds [SessionConfig] instance.
 */
public class SessionConfigBuilder {

    /**
     * The initial bookmarks.
     */
    public var bookmarks: MutableList<Bookmark>? = mutableListOf()

    /**
     * The default type of access.
     */
    public var defaultAccessMode: AccessMode? = null

    /**
     * The target database name.
     */
    public var database: String? = null

    /**
     * The fetch size.
     */
    public var fetchSize: Long? = null

    /**
     * The impersonated user.
     */
    public var impersonatedUser: String? = null

    /**
     * The bookmark manager.
     */
    public var bookmarkManager: BookmarkManager? = null

    /**
     * Builds the [SessionConfig].
     *
     * @return the session config.
     */
    public fun build(): SessionConfig = SessionConfig.builder().apply {
        bookmarks?.let { if (it.isNotEmpty()) withBookmarks(it) }
        defaultAccessMode?.let { withDefaultAccessMode(it) }
        database?.let { withDatabase(it) }
        fetchSize?.let { withFetchSize(it) }
        impersonatedUser?.let { withImpersonatedUser(it) }
        bookmarkManager?.let { withBookmarkManager(it) }
    }.build()

}

/**
 * Creates [SessionConfig] instance.
 */
public fun SessionConfig(
    block: SessionConfigBuilder.() -> Unit
): SessionConfig = SessionConfigBuilder().also(block).build()
