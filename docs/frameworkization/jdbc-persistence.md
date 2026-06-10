# JDBC Persistence

`actiongraph-persistence-jdbc` provides durable repository implementations for v2 suspend/resume, audit trace, external human review tasks, and v4 structured memory.

## Spring Boot Auto-Configuration

Spring Boot applications should prefer the optional starter. It keeps durable persistence out of the base runtime starter while avoiding hand-written repository beans in production services.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-jdbc-spring-boot-starter")
}
```

```yaml
actiongraph:
  persistence:
    jdbc:
      enabled: true
      suspended-run-claim-timeout: 15m
      tables:
        trace-event: actiongraph_trace_event
        suspended-run: actiongraph_suspended_run
        human-review: actiongraph_human_review_task
        memory: actiongraph_memory_record
      blackboard:
        allowed-packages:
          - com.example.business
```

When enabled and a `DataSource` exists, the starter creates:

- `TraceRepository`
- `SuspendedRunRepository`
- `HumanReviewRepository`
- `MemoryRepository`
- `JdbcTraceRunRepository`
- `BlackboardTypeRegistry`

Every bean uses `@ConditionalOnMissingBean`, so applications can override one repository without losing auto-configuration for the others.

## Low-Level Artifact

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-persistence-jdbc")
}
```

The low-level module depends on `actiongraph-core`, `actiongraph-memory`, Jackson, and standard JDBC APIs. Applications provide the JDBC driver and `DataSource`. Use this artifact directly for non-Spring services or when repository construction needs full manual control.

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

Suspended-run snapshots also store a `snapshot_version`. The current `JdbcSuspendedRunRepository.SNAPSHOT_FORMAT_VERSION` is `1`; legacy rows without this column are migrated to version `1` on repository initialization. Unsupported versions raise `UnsupportedSuspendedRunSnapshotVersionException` before Goal/Blackboard JSON is restored. During `claimForResume`, that exception rolls the claim transaction back, so the row does not get stuck in `RESUMING`.

Production applications should also constrain which Blackboard object types can be restored from suspended-run JSON. By default the repository allows all types to preserve backward compatibility. Passing a `BlackboardTypeRegistry` makes restore fail fast before `Class.forName(...)` for any class outside the allowlist:

```java
BlackboardTypeRegistry blackboardTypes = BlackboardTypeRegistry.builder()
        .allowPackage("com.example.claims")
        .allowClass(SharedRuntimeContext.class)
        .build();

SuspendedRunRepository suspendedRunRepository =
        new JdbcSuspendedRunRepository(
                dataSource,
                "actiongraph_suspended_run",
                Duration.ofMinutes(30),
                blackboardTypes
        );
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

`JdbcTraceRunRepository` is a read-only query helper for console and audit screens. It lists run ids from the trace table and returns `TraceRunSummary` with first/last timestamps, latest terminal or suspended status, event count, and trace-chain verification result. It also supports paged/filter queries and trace event details for a selected run:

```java
JdbcTraceRunRepository runs = new JdbcTraceRunRepository(dataSource);
List<TraceRunSummary> recent = runs.findRecentRuns(50);
TraceRunPage completed = runs.findRuns(new TraceRunQuery(
        50,
        0,
        "COMPLETED",
        true
));
List<TraceEvent> trace = runs.findTraceEvents("RUN-1");
```

Control-plane services that want the stable Console port instead of the low-level JDBC model can add `actiongraph-console-jdbc`, which adapts `JdbcTraceRunRepository` to `ConsoleRunRepository`.

Suspended run repository:

- run id
- snapshot format version
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

If a `BlackboardTypeRegistry` is supplied, every suspended-run object class name must match an allowed exact class or package prefix during restore. A disallowed class raises `DisallowedBlackboardTypeException` and stops resume before any business action executes.

If a row has a future or otherwise unsupported `snapshot_version`, resume raises `UnsupportedSuspendedRunSnapshotVersionException` before any snapshot payload is read. This makes cross-deployment incompatibility explicit and auditable instead of surfacing as a partial restore or late business failure.

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
- suspended run Blackboard type allowlists accepting configured classes/packages and rejecting unlisted class names before restore
- suspended run snapshot format versions persisted, legacy rows migrated, unsupported versions rejected, and failed claims rolled back
- a run suspended for human review, resumed by a new executor using JDBC repositories, failing after resume, and compensating an action that completed before suspension
- a pending review task approved externally through JDBC, then consumed by resume to complete the run
- multi-stage human review progression, stale stage rejection, and legacy single-stage migration
- structured memory records saved, queried by scope/type, updated, deleted, and loaded into Blackboard
