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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.neo4j.driver.Record
import org.neo4j.driver.exceptions.NoSuchRecordException
import org.neo4j.driver.exceptions.ResultConsumedException
import org.neo4j.driver.summary.ResultSummary
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * A coroutine result provides a Kotlin-idiomatic way to execute queries on the server and receive records back
 * using non-blocking structured concurrency.
 *
 * **Note:** it mimics [org.neo4j.driver.async.ResultCursor] by adapting it to
 * Kotlin-idiomatic coroutine conventions.
 *
 * The coroutine result is created via [Session.run] and [Transaction.run], for example.
 * Upon creation of the result, the query submitted will not be executed until either the
 * [records] Flow is collected or the [consume] suspend function is called.
 *
 * The records Flow must be fully collected or the summary must be consumed to ensure that resources used
 * by this result are freed correctly.
 *
 * @see org.neo4j.driver.async.ResultCursor
 * @see org.neo4j.driver.Result
 */
public interface Result {

    /**
     * Returns a list of keys.
     *
     * @return a list of keys.
     */
    public fun keys(): List<String>

    /**
     * Returns a cold [Flow] of [Record]s.
     *
     * When the returned Flow is collected, the query is executed and the result is streamed back as records.
     * The Flow emits all records in the result and then completes normally or exceptionally.
     *
     * Before completion or exception, cleanup of result resources such as network connections will be carried out automatically.
     *
     * The Flow collector should complete the collection (either normally or exceptionally) to ensure that resources used by
     * this result are released correctly. Once the Flow completes, the session is ready to run more queries.
     *
     * Cancelling the Flow collection will immediately stop emitting new records, but will not cancel query execution on the server.
     * When the server execution finishes, the Flow will complete normally or exceptionally.
     *
     * The Flow operates using structured concurrency and suspends as needed, without blocking threads.
     *
     * This Flow can only be collected once. Attempting to collect it multiple times will result in an exception.
     *
     * If this Flow is collected after calling [keys], record emission begins after the keys are available.
     * If this Flow is collected after calling [consume], a [ResultConsumedException] will be thrown.
     *
     * @return a cold, single-collector Flow of records.
     * @throws ResultConsumedException if records are consumed more than once.
     */
    public fun records(): Flow<Record>

    /**
     * Asynchronously return the first record in the result, failing if there is not exactly
     * one record left in the stream.
     *
     * @return the first and only record in the stream. Stage will be
     *      completed exceptionally with `NoSuchRecordException` if there is not exactly one record left in the
     * stream. It can also be completed exceptionally if query execution fails.
     * @throws org.neo4j.driver.exceptions.NoSuchRecordException
     */
    public suspend fun single(): Record

    /**
     * Consumes the result and returns the [ResultSummary] that arrives after all records have been processed.
     *
     * Calling this suspend function triggers the execution of the query followed by returning the result summary.
     * If the [records] Flow has not been collected, calling this function will discard all records and return
     * the summary directly once query execution completes.
     *
     * Attempting to call [records] after calling this method will result in a [ResultConsumedException].
     *
     * If called after [keys], the result summary will be returned after query execution without streaming any records to the client.
     * If called after collecting [records], the result summary will be returned after the query execution and record streaming complete.
     *
     * Typically, this method should be called after fully collecting [records] to ensure that all records are processed before obtaining the summary.
     *
     * This method can be called multiple times. When the summary arrives, it is cached locally for all subsequent calls.
     *
     * @return the result summary that arrives after all records have been processed.
     *
     * @see org.neo4j.driver.async.ResultCursor.consumeAsync
     */
    public suspend fun consume(): ResultSummary

    /**
     * Determine if the result is open.
     *
     * The result is considered to be open if it has not been consumed ([consume])
     * and its creator object (e.g. session or transaction) has not been closed
     * (including committed or rolled back).
     *
     * Attempts to access data on closed result will produce [ResultConsumedException].
     *
     * @return `true` if the result is open and `false` otherwise.
     *
     * @see org.neo4j.driver.async.ResultCursor.isOpenAsync
     */
    public suspend fun isOpen(): Boolean

}

/**
 * Returns the single record from the result, or `null` if the result is empty.
 *
 * This extension function collects the result stream and returns:
 * - `null` if there are no records
 * - The single record if there is exactly one record
 * - Throws [org.neo4j.driver.exceptions.NoSuchRecordException] if there are multiple records
 *
 * @return the single record, or `null` if empty
 * @throws org.neo4j.driver.exceptions.NoSuchRecordException if there is more than one record
 * @throws ResultConsumedException if records are consumed more than once
 */
public suspend fun Result.singleOrNull(): Record? {
    var record: Record? = null
    var count = 0

    records().collect { currentRecord ->
        count++
        when (count) {
            1 -> record = currentRecord
            2 -> throw NoSuchRecordException(
                "Expected at most 1 record but found at least 2"
            )
        }
    }

    return record
}

public fun ResultSummary.resultAvailableAfter(): Duration = resultAvailableAfter(
    TimeUnit.MILLISECONDS
).toDuration(
    unit = DurationUnit.MILLISECONDS
)

public fun ResultSummary.resultConsumedAfter(): Duration = resultConsumedAfter(
    TimeUnit.MILLISECONDS
).toDuration(
    unit = DurationUnit.MILLISECONDS
)
