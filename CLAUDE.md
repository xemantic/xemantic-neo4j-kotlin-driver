# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin coroutines adapter for the Neo4j Java driver (async). It wraps Neo4j's AsyncSession API to provide idiomatic Kotlin suspend functions and Flow-based result streaming, enabling structured concurrency without blocking threads.

## Core Architecture

### Adapter Pattern

The library implements an adapter layer between Neo4j's async Java driver and Kotlin coroutines:

- **Public API Layer**: `Session`, `Transaction`, `Result`, `QueryRunner`, `TransactionContext` interfaces in `src/main/kotlin/`
- **Internal Implementation Layer**: `Internal*` classes in `src/main/kotlin/internal/` that wrap Neo4j's `AsyncSession`, `AsyncTransaction`, and `AsyncResultCursor`
- **Conversion Strategy**: All `CompletionStage<T>` from Neo4j async APIs are converted to `suspend` functions using `kotlinx.coroutines.future.await()`

### Key Abstractions

1. **Session (Session.kt)**: Main entry point created via `Driver.coroutineSession()`
   - Wraps `AsyncSession` via `InternalSession`
   - Provides managed transactions: `executeRead()` and `executeWrite()` with automatic retry
   - Provides unmanaged transactions: `beginTransaction()`
   - All operations are `suspend` functions

2. **Result (Result.kt)**: Query results streamed as Kotlin Flow
   - Wraps `AsyncResultCursor` via `InternalResult`
   - `records()` returns `Flow<Record>` for streaming results
   - `consume()` returns `ResultSummary` after discarding unconsumed records
   - Single-collector Flow (cannot be collected multiple times)

3. **Transaction Types**:
   - `Transaction`: For unmanaged transactions with manual `commit()`/`rollback()`
   - `TransactionContext`: For managed transactions used within `executeRead()`/`executeWrite()` callbacks

### Builder Pattern

Configuration uses builder pattern with Kotlin DSL:

```kotlin
driver.coroutineSession { // SessionConfigBuilder
    database = "neo4j"
    fetchSize = 1000L
}

session.executeRead({ // TransactionConfigBuilder
    timeout = 5.seconds
    metadata = mapOf("app" to "myapp")
}) { tx ->
    // transaction code
}
```

## Development Commands

### Build and Test

```bash
./gradlew build                 # Build project and run all tests
./gradlew test                  # Run tests only
./gradlew test --tests "Neo4jCoroutineDriverTest.should perform simple write*"  # Run single test
```

### Code Quality

```bash
./gradlew apiCheck              # Verify binary compatibility with previous versions
./gradlew apiDump               # Update API signatures
./gradlew dependencyUpdates     # Check for dependency updates (rejects alpha/beta/rc)
```

### Documentation

```bash
./gradlew dokkaGeneratePublicationHtml  # Generate API documentation
```

### Publishing

```bash
./gradlew kotlinSourcesJar javadocJar   # Build JAR artifacts
./gradlew publish                        # Publish to Maven (requires signing credentials)
```

## Testing Setup

Tests use:
- **Neo4j Harness**: Embedded Neo4j instance via `@ExtendWith(Neo4jExtension::class)`
- **kotlinx-coroutines-test**: `runTest {}` for coroutine testing
- **xemantic-kotlin-test**: Power-assert style assertions with `assert()` and `should` DSL

Example test pattern:
```kotlin
@Test
fun `test name`() = runTest {
    driver.coroutineSession().use { session ->
        session.executeWrite { tx ->
            tx.run("CREATE (n:Node)").consume()
        }
    }
}
```

## Build Configuration

- **Kotlin**: Explicit API mode enabled (`kotlin.explicitApi()`)
- **Compiler flags**: Progressive mode, extra warnings, context parameters enabled
- **Target versions**: Kotlin 2.2+, Java 17
- **Key dependencies**:
  - `org.neo4j.driver:neo4j-java-driver` (API dependency)
  - `kotlinx-coroutines-core` (API dependency)

## Code Conventions

1. **Explicit visibility**: All public APIs must have explicit visibility modifiers
2. **KDoc**: All public interfaces and functions include comprehensive KDoc comments
3. **Apache License**: All source files include Apache 2.0 license header
4. **Multi-dollar strings**: Use `$$"""..."""` for Cypher queries to avoid `$` escaping
5. **Structured concurrency**: Never use `GlobalScope` or blocking operations

## Important Implementation Notes

- Results must be fully consumed (Flow collected or `consume()` called) to free resources
- Sessions should be used with `.use {}` to ensure proper cleanup
- `records()` Flow can only be collected once; subsequent calls throw `ResultConsumedException`
- After `consume()` is called, `records()` cannot be collected
- Managed transactions (`executeRead`/`executeWrite`) automatically commit/rollback
- Unmanaged transactions require explicit `commit()`/`rollback()` and `close()`