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

import com.xemantic.kotlin.core.SuspendCloseable
import org.neo4j.driver.TransactionConfig
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Logical container for an atomic unit of work.
 * A driver Transaction object corresponds to a server transaction.
 *
 * **Note:** it mimics [org.neo4j.driver.async.AsyncTransaction] by adapting it to
 * Kotlin-idiomatic coroutine conventions.
 *
 * Explicit commit with [commit] or rollback with [rollback] is required.
 * Without explicit commit/rollback corresponding transaction will remain open in the database.
 *
 * ```
 * val tx = session.beginTransaction()
 * try {
 *     tx.run("CREATE (a:Person {name: $name})", parameters("name", "Alice"))
 *     tx.commit()
 * } catch (e: Exception) {
 *     tx.rollback()
 * }
 * ```
 *
 * @see Session
 * @see QueryRunner
 * @see org.neo4j.driver.async.AsyncTransaction
 */
public interface Transaction : QueryRunner, SuspendCloseable {

    /**
     * Commits the transaction.
     *
     * This suspend function completes successfully if the transaction is committed successfully.
     * It throws an exception if there is any error during commit.
     *
     * @see org.neo4j.driver.async.AsyncTransaction.commitAsync
     */
    public suspend fun commit()

    /**
     * Rolls back the transaction.
     *
     * This suspend function completes successfully if the transaction is rolled back successfully.
     * It throws an exception if there is any error during rollback.
     *
     * @see org.neo4j.driver.async.AsyncTransaction.rollbackAsync
     */
    public suspend fun rollback()

    /**
     * Close the transaction. If the transaction has been committed ([commit]) or rolled back ([rollback]),
     * the close is optional and no operation is performed.
     * Otherwise, the transaction will be rolled back by default by this method.
     *
     * @see org.neo4j.driver.async.AsyncTransaction.closeAsync
     */
    public override suspend fun close()

    /**
     * Determine if the transaction is open.
     *
     * @return `true` if transaction is open and `false` otherwise.
     *
     * @see org.neo4j.driver.async.AsyncTransaction.isOpenAsync
     */
    public suspend fun isOpen(): Boolean

}

/**
 * Builds [TransactionConfig] instance.
 */
public class TransactionConfigBuilder {

    /**
     * the transaction timeout
     */
    public var timeout: Duration? = null

    /**
     * the transaction metadata
     */
    public var metadata: Map<String, Any?>? = null

    public fun build(): TransactionConfig = TransactionConfig.builder().apply {
        timeout?.let { withTimeout(it.toJavaDuration()) }
        metadata?.let { withMetadata(metadata) }
    }.build()

}

/**
 * Creates [TransactionConfig] instance.
 */
public fun TransactionConfig(
    block: TransactionConfigBuilder.() -> Unit
): TransactionConfig = TransactionConfigBuilder().also(block).build()
