# Claims Precheck Read-Only Console

F2 productization starts with a static read-only console generated from the claims precheck batch run.

The console is not a server and does not mutate runtime state. It is an HTML artifact written beside the Markdown and CSV reports so reviewers can inspect operational evidence without opening a spreadsheet.

## Output

`ClaimsPrecheckBatchReportWriter` writes three files:

- `claims-precheck-report.md`: business-readable batch summary
- `claims-precheck-results.csv`: machine-readable case results
- `claims-precheck-console.html`: read-only operational console

## Contents

The HTML console shows:

- batch id, environment, sample source, review mode, and generation time
- total runs, completed runs, intercept rate, and audit completeness
- average runtime, business action time, framework time, and review wait time
- per-claim status, intercept flag, audit completeness, trace event count, and timing split

## Generate Locally

```bash
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics \
  --args="--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv \
  --review-callbacks actiongraph-samples/src/main/resources/claims-precheck-review-callbacks.jsonl \
  --review-callback-secret review-secret \
  --report-dir actiongraph-samples/build/reports/claims-precheck-console \
  --batch-id F2-CLAIMS-CONSOLE \
  --environment local"
```

Open:

```text
actiongraph-samples/build/reports/claims-precheck-console/claims-precheck-console.html
```

## Boundary

The generated HTML artifact is a productization seed, not the final console service. It proves the information architecture for run monitoring and audit inspection while keeping the runtime unchanged.

## JDBC Read Model

`actiongraph-persistence-jdbc` provides `JdbcTraceRunRepository` as the low-level read model for trace tables:

```java
JdbcTraceRunRepository runs =
        new JdbcTraceRunRepository(dataSource);

List<TraceRunSummary> recentRuns = runs.findRecentRuns(50);
TraceRunPage completedRuns = runs.findRuns(
        new TraceRunQuery(50, 0, "COMPLETED", true));
TraceRunSummary run = runs.findRun("RUN-1").orElseThrow();
List<TraceEvent> trace = runs.findTraceEvents("RUN-1");
```

Each `TraceRunSummary` includes:

- run id
- first and last trace timestamps
- latest terminal/suspended status from trace data
- trace event count
- trace-chain verification result, including first broken sequence and message
- trace event details for a selected run

This lets a future console list runs directly from the JDBC trace table, page/filter operational views, inspect trace details, and flag tampered or legacy audit chains without replaying business code.

## Console Core

`actiongraph-console-core` packages the read-only control-plane logic without Spring Web or JDBC coupling:

- `ActionGraphConsoleService`
- `ConsoleRunRepository` port
- Console response records for run pages, run summaries, trace events, and errors
- paging and limit validation
- built-in page template rendering helpers

Custom consoles, CLI diagnostics, gateway adapters, or non-Spring services can depend on this module directly and provide their own `ConsoleRunRepository`.

## Console Export

`actiongraph-console-export` packages CSV and JSONL evidence formatting without Spring Web or JDBC coupling:

- run-summary CSV export with paging/status/audit filters
- per-run trace CSV export
- per-run trace JSONL export

It wraps `ActionGraphConsoleService`, so custom batch jobs, audit archive processes, and gateway adapters can reuse the same read-only query service without exposing HTTP endpoints.

## Console JDBC Adapter

`actiongraph-console-jdbc` adapts the JDBC trace read model to the Console port:

```java
ConsoleRunRepository repository =
        new JdbcConsoleRunRepository(dataSource);

ActionGraphConsoleService service =
        new ActionGraphConsoleService(repository, ConsoleOptions.defaults());
```

This keeps the control-plane service independently reusable while still giving JDBC deployments an off-the-shelf adapter.

## Spring Boot Read-Only Control Plane

`actiongraph-console-api-spring-boot-starter` can expose the read model through a servlet application when all of these are true:

- `actiongraph.console.enabled=true`
- `actiongraph-console-api-spring-boot-starter` is on the runtime classpath
- an `ActionGraphConsoleService` or `ConsoleRunRepository` bean is available

The Console API starter wraps `actiongraph-console-core`, exposes only JSON query endpoints, and stays read-only. Add `actiongraph-console-ui-spring-boot-starter` when the same service should also serve the bundled page, add `actiongraph-console-export-spring-boot-starter` when it should expose CSV/JSONL audit export endpoints, or use the compatibility `actiongraph-console-spring-boot-starter` to bring API and UI together. Add `actiongraph-console-jdbc-spring-boot-starter` when a `DataSource` should be adapted into the default JDBC `ConsoleRunRepository`. If the same Spring Boot service also executes or resumes runs, add `actiongraph-jdbc-spring-boot-starter` separately and enable `actiongraph.persistence.jdbc.enabled=true` so runtime repositories are durable too.

```yaml
actiongraph:
  console:
    enabled: true
    path: /actiongraph/console
    token-header: X-ActionGraph-Console-Token
    shared-secret: ${ACTIONGRAPH_CONSOLE_SECRET}
    default-limit: 50
    max-limit: 200
```

Endpoints:

```text
GET /actiongraph/console
GET /actiongraph/console/runs?limit=50&offset=0&status=COMPLETED&auditComplete=true
GET /actiongraph/console/runs/{runId}
GET /actiongraph/console/runs/{runId}/trace
GET /actiongraph/console/runs/export.csv
GET /actiongraph/console/runs/{runId}/trace/export.csv
GET /actiongraph/console/runs/{runId}/trace/export.jsonl
```

The built-in page shows the same read model as a compact operational surface: run list, status/audit filters, selected-run metadata, and a trace timeline. API responses are read-only summaries and trace details: run id, first/last trace timestamps, terminal or suspended status, trace event count, audit-chain verification fields, and individual trace event rows. Export responses return attachment-friendly CSV or JSONL evidence over the same read model. Missing or invalid console tokens return `401 UNAUTHORIZED` for API/export calls when `shared-secret` is configured. A production deployment should still add enterprise authentication/authorization and retention controls.
