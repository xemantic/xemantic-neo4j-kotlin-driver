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

import com.xemantic.neo4j.driver.QueryRunner
import com.xemantic.neo4j.driver.Result
import kotlinx.coroutines.future.await
import org.neo4j.driver.Query
import org.neo4j.driver.Record
import org.neo4j.driver.Value
import org.neo4j.driver.async.AsyncQueryRunner

internal abstract class AbstractInternalQueryRunner(
    private val runner: AsyncQueryRunner
) : QueryRunner {

    override suspend fun run(
        query: String,
        parameters: Value
    ): Result = InternalResult(
        runner.runAsync(query, parameters).await()
    )

    override suspend fun run(
        query: String,
        parameters: Map<String, Any?>
    ): Result = InternalResult(
        runner.runAsync(query, parameters).await()
    )

    override suspend fun run(
        query: String,
        parameters: Record
    ): Result = InternalResult(
        runner.runAsync(query, parameters).await()
    )

    override suspend fun run(
        query: String
    ): Result = InternalResult(
        runner.runAsync(query).await()
    )

    override suspend fun run(
        query: Query
    ): Result = InternalResult(
        runner.runAsync(query).await()
    )

}
