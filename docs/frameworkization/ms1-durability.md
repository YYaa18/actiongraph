# MS1 Durability Delivery Notes

MS1 makes crash recovery opt-in for cross-service orchestration. With
`actiongraph.durability.enabled=false`, existing embedded runtime behavior is
unchanged.

## Implemented

- `SuspendedRun` now represents both human-review suspension and running
  checkpoints through `SnapshotState`.
- `SuspendedRunRepository` has experimental checkpoint, in-flight, heartbeat,
  and stale-running claim methods.
- `GoapExecutor` can write a checkpoint after every successful Action, refresh
  heartbeat in a virtual thread, and recover a claimed checkpoint.
- `RunRecoverer` claims one stale running checkpoint and applies `CONTINUE` or
  `COMPENSATE`.
- JDBC persistence stores `snapshot_state`, `heartbeat_at`, and
  `in_flight_action_id` in the existing suspended-run table.
- `IdempotencyKey` provides the standard
  `X-ActionGraph-Idempotency-Key: runId/actionId/attempt` convention.

## Recovery Semantics

If a checkpoint has `inFlightActionId`, the recovered Action outcome is unknown.
The runtime calls its compensation method first, then replans from the stored
Blackboard. This preserves the existing timeout philosophy: unknown outcome is
not treated as ordinary failure.

## Local Performance

Environment: local H2 in-memory JDBC, 5-step chain, Java virtual threads.

| Scenario | Result |
|---|---:|
| Durability off, H2 trace | 1.365 ms/run, 0.273 ms/step |
| Durability on, H2 trace + checkpoints | 1.633 ms/run, 0.327 ms/step |
| Durability overhead | 0.268 ms/run, 0.054 ms/step |
| Concurrent smoke | 10,000 runs, 0 failures, 2710.7 runs/s |

The measured per-step overhead is well under the MS1 budget of 5 ms/step.

## Operational Notes

- Use JDBC persistence for real deployments; in-memory durability exists for
  tests and demos only.
- Keep `stale-after` much larger than `heartbeat-interval`.
- Set `recoverer-period: 0` when recovery is triggered by an external scheduler
  or control-plane operation.
- Downstream side effects should accept an idempotency key or equivalent
  business de-duplication field.
