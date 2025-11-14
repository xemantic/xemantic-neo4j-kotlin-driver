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

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Common test data classes used across multiple test suites.
 * These classes are used to test Neo4j node and relationship mapping functionality.
 */

@Serializable
data class SimplePerson(
    val name: String,
    val age: Int
)

@Serializable
data class PersonWithNullables(
    val name: String,
    val age: Int?,
    val email: String?
)

@Serializable
data class PersonWithAllTypes(
    val name: String,
    val age: Int,
    val height: Double,
    val isActive: Boolean,
    val salary: Long,
    val birthDate: Instant
)

@Serializable
data class Address(
    val street: String,
    val city: String,
    val zipCode: String
)

@Serializable
data class PersonWithAddress(
    val name: String,
    val age: Int,
    val address: Address
)

@Serializable
data class PersonWithOptionalAddress(
    val name: String,
    val age: Int,
    val address: Address?
)

@Serializable
data class PersonWithList(
    val name: String,
    val tags: List<String>
)

@Serializable
data class PersonWithSet(
    val name: String,
    val skills: Set<String>
)

@Serializable
data class PersonWithNumbers(
    val name: String,
    val scores: List<Int>
)

@Serializable
enum class Role {
    ADMIN,
    USER,
    GUEST
}

@Serializable
data class PersonWithEnum(
    val name: String,
    val role: Role
)

@Serializable
data class PersonWithMap(
    val name: String,
    val metadata: Map<String, String>
)

// Relationship test data classes

@Serializable
data class KnowsSince(val since: Int)

@Serializable
data class WorksFor(
    val position: String,
    val since: Int,
    val salary: Long?
)