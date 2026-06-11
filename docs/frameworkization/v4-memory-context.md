# v4 Structured Memory And Context

v4 memory is implemented as structured records in `actiongraph-core`. There is no vector store or embedding dependency in the runtime kernel.

## Core Types

Package: `com.actiongraph.memory`

- `MemoryScope`
- `MemoryRecord`
- `MemoryRepository`
- `InMemoryMemoryRepository`
- `MemoryContext`
- `MemoryContextLoader`

## Scope

Memory is grouped by:

```java
new MemoryScope("tenant-a", "customer:C001", "renewal")
```

Fields:

- `tenantId`
- `subjectId`
- `namespace`

The `subjectId` is application-defined. Examples include `customer:C001`, `order:O100`, or `user:U123`.

## Records

```java
MemoryRecord preference = MemoryRecord.create(
        scope,
        "preference",
        Map.of("billingCycle", "annual")
);
```

Each record contains:

- stable id
- scope
- type
- string attribute map
- created/updated timestamps

The attribute map is intentionally simple and structured. Applications can evolve type-specific schemas without introducing vector search into the core runtime.

## Loading Context Into Blackboard

```java
MemoryRepository repository = new InMemoryMemoryRepository();
MemoryContextLoader loader = new MemoryContextLoader(repository);

loader.load(
        scope,
        blackboard,
        Condition.of("memory", "CONTEXT_LOADED")
);
```

The loader writes a `MemoryContext` to the Blackboard. It can also write to a keyed Blackboard slot:

```java
loader.load(
        scope,
        BlackboardKey.of(MemoryContext.class, "renewal"),
        blackboard
);
```

Actions can then consume `MemoryContext` just like any other Blackboard object.

## JDBC Persistence

`actiongraph-persistence-jdbc` includes `JdbcMemoryRepository`.

Default table:

```text
actiongraph_memory_record
```

Usage:

```java
MemoryRepository memoryRepository = new JdbcMemoryRepository(dataSource);
```

The repository supports:

- save/upsert by id
- find by id
- find by scope
- find by scope and type
- delete by id

## Current Boundary

This is long-term structured memory, not semantic retrieval. If a future version needs embeddings, it should sit behind a separate retrieval adapter rather than changing the core memory record model.
