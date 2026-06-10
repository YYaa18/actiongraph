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

This is a productization seed, not the final console service. It proves the information architecture for run monitoring and audit inspection while keeping the runtime unchanged. A production console should read from JDBC repositories directly and add authentication, authorization, paging, filters, and deployment-specific retention controls.
