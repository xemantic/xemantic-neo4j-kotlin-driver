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

package com.xemantic.neo4j.driver.internal

import com.xemantic.neo4j.driver.Result
import com.xemantic.neo4j.driver.Session
import com.xemantic.neo4j.driver.Transaction
import com.xemantic.neo4j.driver.TransactionContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.neo4j.driver.*
import org.neo4j.driver.async.AsyncSession

internal class InternalSession(
    private val session: AsyncSession
) : Session, AbstractInternalQueryRunner(runner = session) {

    override suspend fun beginTransaction(
        config: TransactionConfig
    ): Transaction = InternalTransaction(
        session.beginTransactionAsync(config).await()
    )

    override suspend fun <T> executeRead(
        config: TransactionConfig,
        callback: suspend (TransactionContext) -> T
    ): T = coroutineScope {
        session.executeReadAsync<T>(
            { tx ->
                future {
                    callback(InternalTransactionContext(tx))
                }
            },
            config
        ).await()
    }

    override suspend fun <T> executeWrite(
        config: TransactionConfig,
        callback: suspend (TransactionContext) -> T
    ): T = coroutineScope {
        session.executeWriteAsync<T>(
            { tx ->
                future {
                    callback(InternalTransactionContext(tx))
                }
            },
            config
        ).await()
    }

    override fun flow(
        query: Query,
        config: TransactionConfig
    ): Flow<Record> = channelFlow {
        executeRead(config) { tx ->
            tx.run(query).records().collect {
                send(it)
            }
        }
    }

    override suspend fun run(
        query: Query,
        config: TransactionConfig
    ): Result = InternalResult(
        session.runAsync(query, config).await()
    )

    override fun lastBookmarks(): Set<Bookmark> = session.lastBookmarks()

    override suspend fun close() {
        session.closeAsync().await()
    }

}
