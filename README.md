# xemantic-neo4j-kotlin-driver

Kotlin coroutines adapter for the Neo4j Java driver (async)

[<img alt="Maven Central Version" src="https://img.shields.io/maven-central/v/com.xemantic.neo4j/xemantic-neo4j-kotlin-driver">](https://central.sonatype.com/artifact/com.xemantic.neo4j/xemantic-neo4j-kotlin-driver)
[<img alt="GitHub Release Date" src="https://img.shields.io/github/release-date/xemantic/xemantic-neo4j-kotlin-driver">](https://github.com/xemantic/xemantic-neo4j-kotlin-driver/releases)
[<img alt="license" src="https://img.shields.io/github/license/xemantic/xemantic-neo4j-kotlin-driver?color=blue">](https://github.com/xemantic/xemantic-neo4j-kotlin-driver/blob/main/LICENSE)

[<img alt="GitHub Actions Workflow Status" src="https://img.shields.io/github/actions/workflow/status/xemantic/xemantic-neo4j-kotlin-driver/build-main.yml">](https://github.com/xemantic/xemantic-neo4j-kotlin-driver/actions/workflows/build-main.yml)
[<img alt="GitHub branch check runs" src="https://img.shields.io/github/check-runs/xemantic/xemantic-neo4j-kotlin-driver/main">](https://github.com/xemantic/xemantic-neo4j-kotlin-driver/actions/workflows/build-main.yml)
[<img alt="GitHub commits since latest release" src="https://img.shields.io/github/commits-since/xemantic/xemantic-neo4j-kotlin-driver/latest">](https://github.com/xemantic/xemantic-neo4j-kotlin-driver/commits/main/)
[<img alt="GitHub last commit" src="https://img.shields.io/github/last-commit/xemantic/xemantic-neo4j-kotlin-driver">](https://github.com/xemantic/xemantic-neo4j-kotlin-driver/commits/main/)

[<img alt="GitHub contributors" src="https://img.shields.io/github/contributors/xemantic/xemantic-neo4j-kotlin-driver">](https://github.com/xemantic/xemantic-neo4j-kotlin-driver/graphs/contributors)
[<img alt="GitHub commit activity" src="https://img.shields.io/github/commit-activity/t/xemantic/xemantic-neo4j-kotlin-driver">](https://github.com/xemantic/xemantic-neo4j-kotlin-driver/commits/main/)
[<img alt="GitHub code size in bytes" src="https://img.shields.io/github/languages/code-size/xemantic/xemantic-neo4j-kotlin-driver">]()
[<img alt="GitHub Created At" src="https://img.shields.io/github/created-at/xemantic/xemantic-neo4j-kotlin-driver">](https://github.com/xemantic/xemantic-neo4j-kotlin-driver/commits)
[<img alt="kotlin version" src="https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fxemantic%2Fxemantic-neo4j-kotlin-driver%2Fmain%2Fgradle%2Flibs.versions.toml&query=versions.kotlin&label=kotlin">](https://kotlinlang.org/docs/releases.html)
[<img alt="discord users online" src="https://img.shields.io/discord/811561179280965673">](https://discord.gg/vQktqqN2Vn)
[![Bluesky](https://img.shields.io/badge/Bluesky-0285FF?logo=bluesky&logoColor=fff)](https://bsky.app/profile/xemantic.com)

## Quick Start

```kotlin
// Connect to Neo4j
val neo4j = DispatchedNeo4jOperations(
    driver = driver,
    dispatcher = Dispatchers.IO.limitedParallelism(90)
)

// Write data
neo4j.write { tx ->
    tx.run("CREATE (p:Person {name: 'Alice', age: 30})")
}

// Read data
val name = neo4j.read { tx ->
    tx.run(
        "MATCH (p:Person) RETURN p.name AS name"
    ).single()["name"].asString()
}

println(name) // "Alice"
```

See [ReadmeExamples.kt](src/test/kotlin/ReadmeExamples.kt) for all runnable examples.

## Features

### High-Level API
- **[Neo4jOperations](src/main/kotlin/Neo4jOperations.kt)** - Simplified coroutine-friendly interface for common Neo4j operations
- **Automatic session management** - No need to manually manage session lifecycle for simple operations
- **Safe concurrency** - IO dispatcher with limited parallelism (default 90) prevents exhausting the driver's 100 session limit

### Object Mapping
- **Kotlinx.serialization integration** - Map `@Serializable` classes directly to/from Neo4j properties
- **Type-safe conversions** - `toProperties()` and `toObject<T>()` extension functions
- **Instant support** - Automatic conversion between `kotlin.time.Instant` and Neo4j `DateTime`

### Coroutines & Flow
- **Structured concurrency** - All operations use `suspend` functions instead of `CompletionStage`
- **Flow-based streaming** - Stream large result sets efficiently with Kotlin Flow
- **Non-blocking** - Built on Neo4j's async driver, never blocks threads

### Developer Experience
- **Multi-dollar string interpolation** (`$$"""..."""`) - Include `$` in Cypher queries without escaping
- **IntelliJ IDEA integration** - `@Language("cypher")` annotations enable syntax highlighting with the [Graph Database plugin](https://plugins.jetbrains.com/plugin/20417-graph-database)
- **Flexible configuration** - Builder DSL for session and transaction configs with sensible defaults

### Testing
- **`populate()` utility** - Quickly insert test data without boilerplate
- **Resource cleanup** - Automatic cleanup when Flow completes (normally or exceptionally)

## Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.xemantic.neo4j:xemantic-neo4j-kotlin-driver:0.3.1")
}
```

## Getting Started

### Connect to Neo4j

```kotlin
// URI examples: "neo4j://localhost", "neo4j+s://xxx.databases.neo4j.io"
val dbUri = "<database-uri>"
val dbUser = "<username>"
val dbPassword = "<password>"

val driver = GraphDatabase.driver(
    dbUri,
    AuthTokens.basic(dbUser, dbPassword)
).use { driver ->
    driver.verifyConnectivity()
    println("Connection established.")
    // use the driver
}
```

> [!WARNING]
> Always close Driver objects to free up allocated resources. Use Kotlin's `.use { }` function or call `driver.close()` explicitly.

For advanced connection options, see the [Neo4j Java driver documentation](https://neo4j.com/docs/java-manual/current/connect/).

### Create Neo4jOperations

The recommended way to use this library is through the `Neo4jOperations` interface:

```kotlin
val neo4j = DispatchedNeo4jOperations(
    driver = driver,
    dispatcher = Dispatchers.IO.limitedParallelism(90),
    defaultSessionConfig = {
        database = "neo4j"
    },
    defaultTransactionConfig = {
        timeout = 30.seconds
    }
)
```

> [!NOTE]
> The IO dispatcher ensures optimal performance for non-blocking operations. Limiting parallelism to 90 (default driver limit is 100) means operations will suspend instead of throwing exceptions when no free sessions are available.

### Basic Read Query

```kotlin
val peopleCount = neo4j.read { tx ->
    tx.run(
        "MATCH (p:Person) RETURN count(p) AS count"
    ).single()["count"].asInt()
}
```

### Basic Write Query

```kotlin
val summary = neo4j.write { tx ->
    tx.run(
        query = $$"""
            CREATE (a:Person {name: $name})
            CREATE (b:Person {name: $friendName})
            CREATE (a)-[:KNOWS]->(b)
        """.trimIndent(),
        parameters = mapOf(
            "name" to "Alice",
            "friendName" to "David"
        )
    ).consume()
}
println(summary)
```

**Notes:**
- The `query` and `parameters` can be optionally named for clarity
- Multi-dollar interpolation (`$$"""..."""`) allows `$` in queries without escaping
- The final `.consume()` call can be omitted if you don't need the summary

```kotlin
neo4j.write { tx ->
    tx.run(
        query = $$"""
            CREATE (a:Person {name: $name})
            CREATE (b:Person {name: $friendName})
            CREATE (a)-[:KNOWS]->(b)
        """.trimIndent(),
        parameters = mapOf(
            "name" to "Alice",
            "friendName" to "David"
        )
    )
}
```

## Advanced Usage

### Object Mapping with kotlinx.serialization

Map between Neo4j properties and Kotlin data classes using kotlinx.serialization:

```kotlin
@Serializable
data class Person(
    val name: String,
    val email: String,
    val age: Int,
    val city: String,
    val skills: List<String>,
    val active: Boolean,
    val createdAt: Instant = Clock.System.now()
)

val person = Person(
    name = "Alice Johnson",
    email = "alice.johnson@email.com",
    age = 28,
    city = "New York",
    skills = listOf("Python", "JavaScript", "SQL"),
    active = true
)
```

#### Write objects to Neo4j

```kotlin
val createdPerson = neo4j.write { tx ->
    tx.run(
        query = $$"""
            CREATE (person:Person $props)
            SET person.createdAt = datetime()
            RETURN person
        """,
        parameters = mapOf(
            "props" to person.toProperties()
        )
    ).single()["person"].toObject<Person>()
}
```

> [!NOTE]
> The `createdAt` set by the Neo4j server will hold server time. Assuming synchronized clocks, `createdPerson.createdAt` will be greater than or equal to `person.createdAt`.

The `toProperties()` extension function converts any `@Serializable` class to a map of Neo4j-compatible properties.

#### Read objects from Neo4j

```kotlin
val storedPerson = neo4j.read { tx ->
    tx.run(
        "MATCH (p:Person) RETURN p"
    ).single()["p"].toObject<Person>()
}

println(storedPerson)
```

The `toObject<T>()` extension function converts Neo4j nodes, relationships, and records to Kotlin objects.

**Supported sources:**
- **Nodes** - `record["p"].toObject<Person>()`
- **Relationships** - `record["r"].toObject<Knows>()`
- **Records with map projections** - `record.toObject<Person>()` when using `RETURN p.name AS name, p.age AS age`

#### Using map projections

Map projections allow you to create nested structures in query results:

```kotlin
@Serializable
data class Address(val street: String, val city: String, val zipCode: String)

@Serializable
data class PersonWithAddress(val name: String, val age: Int, val address: Address)

val person = neo4j.read { tx ->
    tx.run("""
        MATCH (p:Person)
        RETURN p.name AS name, p.age AS age,
               {street: p.street, city: p.city, zipCode: p.zipCode} AS address
    """.trimIndent()).single().toObject<PersonWithAddress>()
}
```

> [!NOTE]
> Only flat properties are supported in node/relationship storage (primitives, lists of primitives, enums). Nested objects require separate nodes connected by relationships, but can be retrieved using map projections in Cypher queries.

### Flow-based Streaming

Stream large result sets efficiently using Kotlin Flow:

```kotlin
neo4j.flow(
    "MATCH (p:Person) RETURN p ORDER BY p.name"
).collect {
    println(it["p"]["name"].asString())
}
// Prints: Alice, Bob, Charlie...
// Session is automatically closed after flow collection
```

#### Transform and filter

```kotlin
val names = neo4j.flow(
    query = $$"MATCH (p:Person) WHERE p.age > $minAge RETURN p.name AS name ORDER BY p.name",
    parameters = mapOf("minAge" to 28)
).map {
    it["name"].asString()
}.toList()

println(names) // [Alice, Charlie, ...]
```

### Session-based Operations

For complex scenarios requiring multiple transactions on the same session, you can use `neo4j.withSession { }` which provides session lifecycle management:

#### Multiple operations on one session

```kotlin
neo4j.withSession { session ->
    // First: write operation
    session.executeWrite { tx ->
        tx.run("CREATE (p:Person {name: 'Alice'})")
    }

    // Then: read operation on the same session
    val count = session.executeRead { tx ->
        tx.run(
            "MATCH (p:Person) RETURN count(p) as count"
        ).single()["count"].asInt()
    }

    println("Created and counted: $count")
}
```

#### Lower-level driver API

For even more control, you can use `driver.coroutineSession()` directly:

**Write with driver session:**

```kotlin
val summary = driver.coroutineSession().use { session ->
    session.executeWrite { tx ->
        tx.run(
            query = $$"""
                CREATE (a:Person {name: $name})
                CREATE (b:Person {name: $friendName})
                CREATE (a)-[:KNOWS]->(b)
            """.trimIndent(),
            parameters = mapOf(
                "name" to "Alice",
                "friendName" to "David"
            )
        ).consume()
    }
}

println(
    "Created ${summary.counters().nodesCreated()} nodes " +
    "in ${summary.resultAvailableAfter(TimeUnit.MILLISECONDS)} ms."
)
```

**Read with driver session:**

```kotlin
val (names, readSummary) = driver.coroutineSession().use { session ->
    session.executeRead { tx ->
        val result = tx.run(
            "MATCH (p:Person)-[:KNOWS]->(:Person) RETURN p.name AS name"
        )
        val names = result.records().toList().map {
            it["name"].asString()
        }
        val summary = result.consume()
        names to summary
    }
}

println(
    "The query ${readSummary.query().text()} " +
    "returned ${names.size} records " +
    "in ${readSummary.resultAvailableAfter(TimeUnit.MILLISECONDS)} ms."
)
println("Returned names: $names")
```

**Session and transaction configuration:**

```kotlin
driver.coroutineSession { // session config
    database = "neo4j"
}.use { session ->
    session.executeRead({ // transaction config
        timeout = 5.seconds
        metadata = mapOf("appName" to "peopleTracker")
    }) { tx ->
        tx.run("MATCH (p:Person) RETURN p").records().collect {
            println(it)
        }
    }
}
```

### Populate Utility for Testing

Quickly set up test data:

```kotlin
neo4j.populate("""
    CREATE (p1:Person {name: 'Alice', age: 30})
    CREATE (p2:Person {name: 'Bob', age: 25})
    CREATE (p1)-[:KNOWS]->(p2)
""".trimIndent())
```

The `populate()` function handles session and transaction management automatically.

## API Comparison

### Simple operations - Use Neo4jOperations shortcuts

```kotlin
// ✅ Recommended for simple single-transaction operations
val count = neo4j.read { tx ->
    tx.run(
        "MATCH (p:Person) RETURN count(p) as count"
    ).single()["count"].asInt()
}
```

### Multiple transactions - Use withSession

```kotlin
// ✅ Recommended when you need multiple transactions on the same session
neo4j.withSession { session ->
    session.executeWrite { tx ->
        tx.run("CREATE (p:Person {name: 'Alice'})")
    }
    val count = session.executeRead { tx ->
        tx.run(
            "MATCH (p:Person) RETURN count(p) as count"
        ).single()["count"].asInt()
    }
    println("Created and counted: $count")
}
```

### Advanced control - Use driver.coroutineSession()

```kotlin
// ✅ Use when you need maximum control over session lifecycle
driver.coroutineSession().use { session ->
    // Full control over session configuration and lifecycle
    val summary = session.executeWrite { tx ->
        tx.run("CREATE (p:Person {name: 'Bob'})").consume()
    }
    println("Nodes created: ${summary.counters().nodesCreated()}")
}
```

## Important Notes

### Resource Management

- **Sessions**: Always use `.use { }` to ensure proper cleanup
- **Results**: Must be fully consumed (Flow collected or `consume()` called)
- **Transactions**: Managed transactions auto-commit/rollback; unmanaged require explicit `commit()`/`rollback()`

### Result Consumption

- `records()` Flow can only be collected **once**
- After `consume()` is called, `records()` cannot be collected
- Results are automatically consumed when Flow collection completes

### Limitations

- **Nested objects**: Not supported in property mapping - use separate nodes with relationships
- **Multi-collector Flows**: Each result's `records()` can only be collected once

## Contributing

See [CLAUDE.md](CLAUDE.md) for development guidelines and architecture overview.

## License

Apache License 2.0 - see [LICENSE](LICENSE)