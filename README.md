# fsm-library

A Java 17 library for building **stateful, resumable finite state machines**.

Define your workflow once as a type-safe state machine. Run it synchronously in a single process, or drive it across multiple HTTP requests with full failure recovery and automatic retry — without writing any of the orchestration plumbing yourself.

> **Status:** 1.0.x-beta — public API is stable but may see minor changes before 1.0.0 GA.

---

## Features

- **Fluent DSL** — define states, transitions, guards, and sub-steps with a readable builder
- **Sub-step tracking** — granular named work units; completed ones are skipped on resume
- **Snapshot-based persistence** — failures are checkpointed; the machine resumes exactly where it stopped
- **Automatic retry** — exponential backoff, fixed delay, or no-auto-retry; pluggable policies
- **HTTP manager** — per-execution locking, context loading, and the full load→execute→save cycle in one call
- **Explicit initialization** — `initialize()` method for explicit setup in initial state without events
- **State validation** — safe `isInitialState()` and `isTerminal()` checks without hardcoding state names
- **Exception details** — `getRootCause()` on results for targeted error handling
- **Startup recovery** — `recoverPendingRetries()` reschedules in-flight retries after a process restart
- **Event listeners** — lifecycle hooks for observability, auditing, and metrics
- **JDBC persistence** — `JdbcSnapshotRepository` for distributed multi-replica deployments (PostgreSQL, MySQL, H2, SQLite, Oracle)
- **Spring Boot autoconfiguration** — optional `fsm-spring-boot-starter-jdbc` for zero-config JDBC setup
- **Pluggable storage** — swap `InMemorySnapshotRepository` (tests), `FileSnapshotRepository` (single-JVM), or `JdbcSnapshotRepository` (distributed)
- **Spring-friendly** — class-based `StateConfigurer` and `SubStepHandler` interfaces for dependency injection

---

## Quick start

```xml
<dependency>
    <groupId>io.hypercell</groupId>
    <artifactId>fsm-core</artifactId>
    <version>1.0.0-beta</version>
</dependency>
```

```java
StateMachineDefinition<OrderContext> machine = StateMachine.<OrderContext>define("order-workflow")
    .initial("PENDING")
    .snapshotRepository(StateMachine.inMemoryRepository())
    .listener(StateMachine.loggingListener("[ORDER]"))
    .state("PENDING")
        .on("APPROVE").to("PROCESSING").end()
        .on("CANCEL").to("CANCELLED").end()
        .and()
    .state("PROCESSING")
        .subStep("reserve-stock",  ctx -> reserveStock(ctx))
        .subStep("charge-payment", ctx -> chargePayment(ctx))
        .subStep("send-email",     ctx -> sendConfirmation(ctx))
        .on("COMPLETE").to("SHIPPED").end()
        .and()
    .state("SHIPPED").terminal().and()
    .state("CANCELLED").terminal().and()
    .build();

StateMachineInstance<OrderContext> instance = machine.newInstance(new OrderContext(orderId));
instance.trigger("APPROVE");     // PENDING → PROCESSING (runs all 3 sub-steps)
instance.trigger("COMPLETE");    // PROCESSING → SHIPPED (terminal, status = COMPLETED)
```

---

## Documentation

| Document | Description |
|---|---|
| [Concepts](docs/01-concepts.md) | Mental model — states, transitions, sub-steps, lifecycle |
| [Getting started](docs/02-getting-started.md) | Step-by-step tutorial |
| [Use cases](docs/03-use-cases.md) | Recipes: HTTP manager, auto-retry, Spring DI, guards, state validation, JDBC, exception handling |
| [Threading & safety](docs/04-threading-and-safety.md) | Thread-safety contracts for every class |
| [Persistence & retry](docs/05-persistence-and-retry.md) | Snapshots, SnapshotStatus, retry policies, InMemory/File/JDBC repositories, startup recovery |
| [API reference](docs/06-api-reference.md) | Full reference for all public types, including ContextLoader and JdbcSnapshotRepository |
| [JDBC & Spring Boot](docs/08-jdbc-and-spring-boot.md) | Distributed deployments with `JdbcSnapshotRepository` and Spring Boot autoconfiguration |
| [Limitations & roadmap](docs/07-limitations.md) | Known bugs, gaps, and future improvement ideas |

---

## Project structure

```
fsm-library/
├── fsm-core/                       Core library (Java 17, SLF4J logging)
│   └── src/main/java/.../fsm/
│       ├── StateMachine.java       entry point (static factories)
│       ├── builder/                fluent DSL
│       ├── core/                   public interfaces (definitions, instances, managers)
│       ├── execution/              runtime engine (internal)
│       ├── exception/              typed exceptions
│       ├── listener/               event bus & lifecycle callbacks
│       ├── manager/                HTTP request orchestration
│       ├── resume/                 snapshots & persistence
│       └── retry/                  retry policies & scheduling
│
├── fsm-jdbc/                       JDBC-based persistence (PostgreSQL, MySQL, H2, SQLite, Oracle)
│   └── JdbcSnapshotRepository      optimistic-locking-based snapshots for distributed deployments
│
├── fsm-spring-boot-starter-jdbc/   Spring Boot autoconfiguration for JDBC
│   └── auto-configures JdbcSnapshotRepository using Spring's DataSource
│
└── fsm-examples/                   Runnable examples demonstrating all patterns
    ├── SynchronousWorkflowExample  direct instance use
    ├── HttpManagerExample          HTTP-driven manager with context loaders
    └── FileSnapshotRetryExample    file-based persistence with auto-retry
```

---

## License

Copyright 2026 Ahmed Mehanna. Licensed under the [Apache License, Version 2.0](LICENSE).
