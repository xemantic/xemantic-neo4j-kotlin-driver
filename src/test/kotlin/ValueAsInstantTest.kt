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

import org.neo4j.driver.internal.value.DateTimeValue
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.time.toKotlinInstant

class ValueAsInstantTest  {

    @Test
    fun `should get Instant from DateTimeValue`() {
        // given
        val now = ZonedDateTime.now()

        // when
        val instant = DateTimeValue(now).asInstant()

        // then
        assert(instant == now.toInstant().toKotlinInstant())
    }

}
