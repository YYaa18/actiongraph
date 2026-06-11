# MS2 Verification Evidence

Date: 2026-06-12

## Commands

```bash
./gradlew build --rerun-tasks
```

Result:

- Build: successful
- Gradle tasks: 76 executed
- JUnit XML suites: 91
- Tests: 398
- Failures: 0
- Errors: 0
- Skipped: 1

Additional focused runs during implementation:

```bash
./gradlew :actiongraph-core:test --tests com.actiongraph.events.ExternalEventGatewayTest
./gradlew :actiongraph-persistence-jdbc:test --tests com.actiongraph.persistence.jdbc.JdbcExecutorPersistenceTest
./gradlew :actiongraph-spring-boot-starter:test --tests com.actiongraph.events.spring.ActionGraphEventCallbackWebAutoConfigurationTest
```

## Covered MS2 Paths

- `ActionResult.waiting(...)` suspends the run as `SUSPENDED_WAITING_EVENT`.
- `WAITING_EVENT` snapshots persist event type, correlation id, deadline, executed actions, and compensation stack.
- `ExternalEventGateway.deliver(...)` atomically claims a waiting event and resumes the same run id.
- Concurrent delivery returns exactly one `RESUMED`; the overlapping duplicate returns `ALREADY_HANDLED`; post-completion duplicate returns `NOT_FOUND`.
- Missing applier returns `APPLIER_MISSING` without claiming the waiting snapshot.
- Event timeout sweeper compensates the waiting action and returns `FAILED_COMPENSATED`.
- Event delivery can be followed by human-review suspension in the same run with continuous trace hash chain.
- Crash after event checkpoint writeback is recoverable by the MS1 recoverer without event redelivery.
- Crash/failure before checkpoint writeback restores the waiting snapshot for redelivery.
- JDBC repository persists and claims waiting-event fields.
- Optional Spring HTTP callback endpoint enforces shared-secret token configuration and supports custom path/header.

## DHK Status

`docs/dhk-integration.md` describes the DHK evidence flow, but this workspace
does not currently contain `.agents` / `.codex` DHK configuration and the `dhk`
CLI is not installed on PATH. The evidence above is therefore Gradle/JUnit
evidence, not a forged DHK verification artifact.
