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

import org.neo4j.configuration.connectors.BoltConnector
import org.neo4j.configuration.connectors.HttpConnector
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.neo4j.harness.Neo4j
import org.neo4j.harness.internal.InProcessNeo4jBuilder

object SharedNeo4jInstance {

    private val neo4j: Neo4j by lazy {
        InProcessNeo4jBuilder()
            .withDisabledServer()
            .withConfig(HttpConnector.enabled, false)
            .withConfig(BoltConnector.enabled, true)
            .build()
    }

    val driver: Driver by lazy {
        GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none())
    }

    fun cleanDatabase() {
        driver.executableQuery("MATCH (n) DETACH DELETE n").execute()
    }

}
