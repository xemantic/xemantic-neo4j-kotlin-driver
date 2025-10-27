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

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A `suspend` variant of [AutoCloseable].
 */
public interface SuspendCloseable {

    public suspend fun close()

}

public suspend inline fun <T : SuspendCloseable?, R> T.use(
    block: (T) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        this.closeFinally(exception)
    }
}

@PublishedApi
internal suspend fun SuspendCloseable?.closeFinally(
    cause: Throwable?
): Unit = when {
    this == null -> {}
    cause == null -> close()
    else -> try {
        close()
    } catch (closeException: Throwable) {
        cause.addSuppressed(closeException)
    }
}
