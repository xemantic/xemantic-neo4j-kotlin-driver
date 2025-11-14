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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import org.neo4j.driver.Record
import org.neo4j.driver.async.ResultCursor
import org.neo4j.driver.exceptions.ResultConsumedException
import org.neo4j.driver.summary.ResultSummary
import kotlin.concurrent.atomics.AtomicBoolean

internal class InternalResult(
    private val cursor: ResultCursor
) : Result {

    private val recordsConsumed = AtomicBoolean(false)

    override fun keys(): List<String> = cursor.keys()

    override fun records(): Flow<Record> = flow {

        if (!recordsConsumed.compareAndSet(
                expectedValue = false,
                newValue = true
        )) {
            throw ResultConsumedException(
                "Records can only be consumed once"
            )
        }

        try {
            while (true) {
                val record = cursor.nextAsync().await() ?: break
                emit(record)
            }
        } finally {
            consume()
        }

    }

    override suspend fun single(): Record {
        if (!recordsConsumed.compareAndSet(
                expectedValue = false,
                newValue = true
        )) {
            throw ResultConsumedException(
                "Records can only be consumed once"
            )
        }
        return cursor.singleAsync().await()
    }

    override suspend fun consume(): ResultSummary = cursor.consumeAsync().await()

    override suspend fun isOpen(): Boolean = cursor.isOpenAsync().await()

}
