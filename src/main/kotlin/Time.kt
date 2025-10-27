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

import org.neo4j.driver.Value
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * Returns the value as a kotlin.time.[Instant], if possible.
 *
 * @return the value as an [Instant], if possible
 * @throws org.neo4j.driver.exceptions.value.Uncoercible if value types are incompatible
 * @throws java.time.DateTimeException if zone information supplied by server is not supported by driver runtime
 */
public fun Value.asInstant(): Instant = asZonedDateTime().toInstant().toKotlinInstant()
