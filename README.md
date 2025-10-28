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

## TL;DR

A good example might be worth thousand words:

```kotlin
driver.coroutineSession().use { session ->
    session.executeWrite { tx ->
        tx.run("""
            CREATE (alice:Person {
                id: 'p001',
                name: 'Alice Johnson',
                email: 'alice.johnson@email.com',
                age: 28,
                city: 'New York',
                skills: ['Python', 'JavaScript', 'SQL'],
                active: true,
                createdAt: datetime('2023-01-15T10:30:00')
            });
        """.trimIndent())
    }
}

val person = driver.coroutineSession().use { session ->
    session.executeRead { tx ->
        tx.run(
            "MATCH (p:Person) RETURN p"
        ).records().map { record ->
            record["p"].let { p ->
                Person(
                    id =        p["id"].asString(),
                    name =      p["name"].asString(),
                    email =     p["email"].asString(),
                    age =       p["age"].asInt(),
                    city =      p["city"].asString(),
                    skills =    p["skills"].asList { it.asString() },
                    active =    p["active"].asBoolean(),
                    createdAt = p["createdAt"].asInstant()
                )
            }
        }.first()
    }
}

println(person)
```

## Usage

In `build.gradle.kts` add:

```kotlin
dependencies {
    implementation("com.xemantic.neo4j:xemantic-neo4j-kotlin-driver:0.2.0")
}
```

## Connection

### Connect to the database

```kotlin
fun main() {
    // URI examples: "neo4j://localhost", "neo4j+s://xxx.databases.neo4j.io"
    val dbUri = "<database-uri>"
    val dbUser = "<username>"
    val dbPassword = "<password>"

    GraphDatabase.driver(dbUri, AuthTokens.basic(dbUser, dbPassword)).use {
        it.verifyConnectivity()
        println("Connection established.")
    }
}
```

### Close connections

> [!WARNING]
> Always close Driver objects to free up all allocated resources, even upon unsuccessful connection or runtime errors.
 
Kotlin provides [use](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io/use.html) function to ensure that a resource is closed (equivalent of try-with-resources in Java). If it is not suitable for your use case, remember to call the `Driver.close()` function explicitly.

### Advanced connection information

Refer to the original documentation of Java driver:

https://neo4j.com/docs/java-manual/current/connect/
https://neo4j.com/docs/java-manual/current/connect-advanced/

## Coroutine sessions

### Creating coroutine session

```kotlin
driver.coroutineSession().use { session ->
    // use session
}
```

> [!NOTE]
> The `coroutineSession()` function returns an instance of [Session](src/main/kotlin/Session.kt), which is adapting the `AsyncSession` to Kotlin-idiomatic coroutines. Instead of `CompletionStage` instances being returned for asynchronous callbacks, all the functions are marked with `suspend` keyword. Check out [Kotlin coroutines](https://kotlinlang.org/docs/coroutines-overview.html) official documentation for details.

### Close sessions

Each connection pool has a finite number of sessions, so if you open sessions without ever closing them, your application could run out of them. It is thus recommended to create sessions with the `use` function (Kotlin-idiomatic equivalent of try-with-resources Java statement), which automatically closes them when the application is done with them. When a session is closed, it is returned to the connection pool to be later reused.

If you do not open sessions with `use` functions, remember to call the `.close()` function when you have finished using them.

```kotlin
val session = driver.coroutineSession()

// session usage

session.close()
```

## Query the database

### Write to the database

```kotlin
val summary = driver.coroutineSession().use { session ->
    session.executeWrite { tx ->
        tx.run(
            // multi-dollar interpolation allows to include $ without escaping
            query = $$"""
                CREATE (a:Person {name: $name})
                CREATE (b:Person {name: $friendName})
                CREATE (a)-[:KNOWS]->(b)
            """.trimIndent(),
            // named `query` and `parameters` parameters can be skipped if you prefer
            parameters = mapOf(
                "name" to "Alice",
                "friendName" to "David"
            )
        ).consume() // ensures that the ResultSummary is returned
    }
}

println(
    "Created ${summary.counters().nodesCreated()}" +
            " in ${summary.resultAvailableAfter(TimeUnit.MILLISECONDS)} ms."
)
```

See [Neo4jCoroutineDriverTest](src/test/kotlin/Neo4jCoroutineDriverTest.kt) with a full running example.

> [!NOTE]
> This code needs to be called in the coroutine scope. Wrapping such an invocation in the `suspend` function is usually a way to go.

> [!TIP]
> If you are using IntelliJ, installing the [Graph Database](https://plugins.jetbrains.com/plugin/20417-graph-database) plugin will automatically highlight the Cypher query code inside multiline strings.

### Read from the database


```kotlin
val (names, readSummary) = driver.coroutineSession().use { session ->
    session.executeRead { tx ->
        val result = tx.run(
            "MATCH (p:Person)-[:KNOWS]->(:Person) RETURN p.name AS name"
        )
        val names = result.records().map {
            it["name"].asString()
        }.toList()
        val summary = result.consume()
        names to summary
    }
}

println(
    "The query ${readSummary.query()} " +
            "returned ${names.size} records " +
            "in ${readSummary.resultAvailableAfter(TimeUnit.MILLISECONDS)} ms."
)
println("Returned names: $names")
```

## Transaction configuration

```kotlin
driver.coroutineSession { // session config
    database = "<database-name>"
}.use { session ->
    val result = session.executeRead({ // transaction config
        timeout = 5.seconds
        metadata = mapOf("appName" to "peopleTracker")
    }) { tx ->
        tx.run("MATCH (p:Person) RETURN p")
    }
    result.records().collect {
        println(it)
    }
}
```
