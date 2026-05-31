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
- **Startup recovery** — `recoverPendingRetries()` reschedules in-flight retries after a process restart
- **Event listeners** — lifecycle hooks for observability, auditing, and metrics
- **Pluggable storage** — swap `InMemorySnapshotRepository` (tests) or `FileSnapshotRepository` (single-JVM) for your own database/Redis implementation
- **Spring-friendly** — class-based `StateConfigurer` and `SubStepHandler` interfaces for dependency injection

---

## Quick start

```xml
<dependency>
    <groupId>hypercell.opensource.stateful.fsm</groupId>
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
| [Use cases](docs/03-use-cases.md) | Recipes: HTTP manager, auto-retry, Spring DI, guards, recovery |
| [Threading & safety](docs/04-threading-and-safety.md) | Thread-safety contracts for every class |
| [Persistence & retry](docs/05-persistence-and-retry.md) | Snapshots, SnapshotStatus, retry policies, startup recovery |
| [API reference](docs/06-api-reference.md) | Full reference for all public types |
| [Limitations & roadmap](docs/07-limitations.md) | Known bugs, gaps, and future improvement ideas |

---

## Project structure

```
fsm-library/
└── fsm-core/          Java 17, Maven, SLF4J logging
    └── src/main/java/hypercell/opensource/stateful/fsm/
        ├── StateMachine.java          entry point (static factories)
        ├── builder/                   fluent DSL
        ├── core/                      public interfaces
        ├── execution/                 runtime (internal)
        ├── exception/                 typed exceptions
        ├── listener/                  event bus & lifecycle callbacks
        ├── manager/                   HTTP request orchestration
        ├── resume/                    snapshots & persistence
        └── retry/                     retry policies & scheduling
```

---

## License

Copyright 2026 Ahmed Mehanna. Licensed under the [Apache License, Version 2.0](LICENSE).
