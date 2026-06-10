# JDBC Persistence

`actiongraph-persistence-jdbc` provides durable repository implementations for v2 suspend/resume, audit trace, external human review tasks, and v4 structured memory.

## Artifacts

```kotlin
dependencies {
    implementation("com.actiongraph:actiongraph-persistence-jdbc:0.1.0")
}
```

The module depends on `actiongraph-core`, Jackson, and standard JDBC APIs. Applications provide the JDBC driver and `DataSource`.

## Repositories

```java
DataSource dataSource = ...

TraceRepository traceRepository =
        new JdbcTraceRepository(dataSource);

SuspendedRunRepository suspendedRunRepository =
        new JdbcSuspendedRunRepository(dataSource);

HumanReviewRepository humanReviewRepository =
        new JdbcHumanReviewRepository(dataSource);

MemoryRepository memoryRepository =
        new JdbcMemoryRepository(dataSource);

GoapExecutor executor = new GoapExecutor(
        planner,
        policyGuard,
        humanReviewPolicy,
        traceRepository,
        suspendedRunRepository
);
```

The JDBC repositories create their tables on construction if they do not exist.

Default tables:

- `actiongraph_trace_event`
- `actiongraph_suspended_run`
- `actiongraph_human_review_task`
- `actiongraph_memory_record`

Custom table names are supported:

```java
new JdbcTraceRepository(dataSource, "my_trace_event");
new JdbcSuspendedRunRepository(dataSource, "my_suspended_run");
new JdbcHumanReviewRepository(dataSource, "my_human_review_task");
new JdbcMemoryRepository(dataSource, "my_memory_record");
```

Table names are restricted to letters, numbers, and underscores.

Suspended run claim timeout is configurable. The default is 15 minutes; after this window, a `RESUMING` row can be claimed again to recover from a process crash during `resume`.

```java
new JdbcSuspendedRunRepository(dataSource, Duration.ofMinutes(30));
new JdbcSuspendedRunRepository(dataSource, "my_suspended_run", Duration.ofMinutes(30));
```

## What Is Persisted

Trace repository:

- run id
- sequence number
- timestamp
- event type
- action id
- detail
- event data JSON
- previous event hash
- event hash

`JdbcTraceRepository.appendAll(...)` writes trace events with JDBC batch execution. The executor buffers normal run events and flushes at terminal states; it also flushes before external human review, before suspension is saved, and before compensation begins so audit-critical boundaries are durable.

Trace hashes are calculated in core before persistence, after `DataMaskingPolicy` has processed detail/data. The JDBC table stores `prev_hash` and `hash`; existing tables are migrated with nullable columns, and pre-F0 rows with empty hashes are reported invalid by `TraceChainVerifier` rather than backfilled.

Suspended run repository:

- run id
- goal name and target conditions
- Blackboard conditions
- Blackboard objects as typed JSON payloads, including Blackboard key id
- executed action ids
- compensation stack action ids
- pending action id
- suspension message
- resume status and claim timestamp

Suspended-run Blackboard snapshots are intentionally not masked. They are recovery state, not a human-readable audit surface, so masking them would make `resume` lossy. Treat the suspended-run table as highly sensitive runtime state: only the runtime service account should read or write it.

Human review repository:

- run id
- action id
- risk level
- plan preview action ids
- current condition state
- blackboard preview
- review attributes such as amount escalation metadata
- approval stages
- current stage index
- stage decisions
- pending/approved/denied decision
- reviewer
- message
- created/updated timestamps

Memory repository:

- memory id
- tenant id
- subject id
- namespace
- type
- structured attributes JSON
- created/updated timestamps

On resume, the executor atomically claims the suspended run before restoring the Blackboard. JDBC uses a status transition from `SUSPENDED` to `RESUMING`; duplicate resume attempts for the same `runId` receive no snapshot, surface as `SuspendedRunNotClaimableException`, and stop before business side effects. After a successful claim, the executor rehydrates the compensation stack from the supplied `ActionRegistry`, continues the same trace sequence, and deletes the suspended run when the resumed run reaches a terminal non-suspended state.

## Serialization Boundary

Blackboard objects are serialized with Jackson using their concrete runtime class names and Blackboard key ids. This keeps the core runtime free of persistence concerns, but it means persisted domain objects should be Jackson-serializable and class names must remain stable across deployment versions.

`DataMaskingPolicy` masks trace detail/data and human-review previews before they reach JDBC repositories. It does not mask suspended-run snapshots.

For schema or package migrations, introduce an application-level migration step before calling `resume`.

## Verification

The module tests cover:

- trace events persisted and read back in sequence order
- trace events persisted through batch append
- trace hash columns persisted, read back, and used to detect tampering
- legacy trace tables migrated with empty hashes treated as pre-F0 data
- suspended run snapshots restored with Goal, Blackboard, executed actions, and compensation stack
- suspended run resume claims succeeding only once
- multiple same-type Blackboard values restored by key
- a run suspended for human review, resumed by a new executor using JDBC repositories, failing after resume, and compensating an action that completed before suspension
- a pending review task approved externally through JDBC, then consumed by resume to complete the run
- multi-stage human review progression, stale stage rejection, and legacy single-stage migration
- structured memory records saved, queried by scope/type, updated, deleted, and loaded into Blackboard
