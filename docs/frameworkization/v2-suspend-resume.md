# v2 Suspend / Resume Semantics

## What Changed

Human review is no longer only a synchronous stop. When a review policy returns `PENDING`, the executor now stores a `SuspendedRun` in a `SuspendedRunRepository` and returns:

```java
RunStatus.SUSPENDED_PENDING_REVIEW
```

The suspended snapshot contains:

- `runId`
- `Goal`
- `Blackboard`
- executed action ids
- compensation stack action ids
- pending review action id
- suspension message

The default repository is `InMemorySuspendedRunRepository`. It proves the runtime semantics, but it is not durable across process restarts. A database-backed repository can replace it without changing planner/action code.

## Resume

Resume through:

```java
executor.resume(runId, actions, registry)
```

Resume first atomically claims the suspended snapshot from `SuspendedRunRepository`. If the same `runId` is resumed concurrently by duplicate callbacks, retrying consumers, or multiple application instances, only one caller receives the snapshot and continues business execution. Other callers fail with `SuspendedRunNotClaimableException` before side effects.

Resume uses the same `runId` and continues trace sequence numbering for the same run. The planner starts from the suspended Blackboard conditions, so it naturally skips already-satisfied steps.

## Compensation After Resume

The compensation stack is restored from the suspended snapshot. If a resumed action fails, actions that succeeded before suspension are still compensated.

This is covered by `PolicyExecutionTest.resumedRunFailureCompensatesActionsFromBeforeSuspension`.

Concurrent resume safety is covered by `PolicyExecutionTest.concurrentResumeOfSameRunClaimsSuspendedSnapshotOnlyOnce`.

## Current Limits

- `InMemorySuspendedRunRepository` keeps object references, not serialized state.
- JDBC suspend/resume serializes Blackboard objects by class name, so application package names and persisted payload shapes need migration discipline across deployments.
- Resuming requires the caller to provide compatible `actions` and `registry`.
- Pending human review uses `SUSPENDED_PENDING_REVIEW`; there is no separate waiting status.
