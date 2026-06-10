package com.actiongraph.samples.claimsprecheck.batch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ClaimsPrecheckBatchReportWriter {
    public static final String MARKDOWN_REPORT = "claims-precheck-report.md";
    public static final String CSV_RESULTS = "claims-precheck-results.csv";
    public static final String HTML_CONSOLE = "claims-precheck-console.html";

    public void write(Path reportDir, ClaimsPrecheckBatchMetrics metrics) {
        write(reportDir, metrics, ClaimsPrecheckBatchReportMetadata.defaults(List.of()));
    }

    public void write(
            Path reportDir,
            ClaimsPrecheckBatchMetrics metrics,
            ClaimsPrecheckBatchReportMetadata metadata
    ) {
        Objects.requireNonNull(reportDir, "reportDir");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(metadata, "metadata");
        try {
            Files.createDirectories(reportDir);
            Files.writeString(reportDir.resolve(MARKDOWN_REPORT), markdown(metrics, metadata), StandardCharsets.UTF_8);
            Files.writeString(reportDir.resolve(HTML_CONSOLE), htmlConsole(metrics, metadata), StandardCharsets.UTF_8);
            ClaimsPrecheckBatchCsv.writeResults(reportDir.resolve(CSV_RESULTS), metrics);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot write claims precheck report to " + reportDir, ex);
        }
    }

    private String markdown(ClaimsPrecheckBatchMetrics metrics, ClaimsPrecheckBatchReportMetadata metadata) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Claims Precheck Batch Metrics\n\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Batch ID: ").append(metadata.batchId()).append("\n");
        builder.append("- Environment: ").append(metadata.environment()).append("\n");
        builder.append("- Sample Source: ").append(metadata.sampleSource()).append("\n");
        builder.append("- Review Mode: ").append(metadata.reviewMode()).append("\n");
        builder.append("- Simulated Review Wait Ms: ").append(metadata.simulatedReviewWaitMillis()).append("\n");
        builder.append("- External Review Inputs: ").append(metadata.externalReviewInputCount()).append("\n");
        builder.append("- Total Runs: ").append(metrics.totalRuns()).append("\n");
        builder.append("- Completed: ").append(metrics.completedRuns()).append("\n");
        builder.append("- Intercepted: ").append(metrics.interceptedRuns()).append("\n");
        builder.append("- Failed: ").append(metrics.failedRuns()).append("\n");
        builder.append("- Audit Complete: ").append(metrics.auditCompleteRuns()).append("\n");
        builder.append("- Intercept Rate: ").append(percent(metrics.interceptRate())).append("\n");
        builder.append("- Audit Completeness Rate: ").append(percent(metrics.auditCompletenessRate())).append("\n");
        builder.append("- Average Runtime Ms: ")
                .append(String.format(Locale.ROOT, "%.3f", metrics.averageRuntimeMillis()))
                .append("\n");
        builder.append("- Average Business Action Ms: ")
                .append(String.format(Locale.ROOT, "%.3f", metrics.averageBusinessActionMillis()))
                .append("\n");
        builder.append("- Average Framework Ms: ")
                .append(String.format(Locale.ROOT, "%.3f", metrics.averageFrameworkMillis()))
                .append("\n");
        builder.append("- Average Review Wait Ms: ")
                .append(String.format(Locale.ROOT, "%.3f", metrics.averageReviewWaitMillis()))
                .append("\n\n");
        if (!metadata.limitRules().isEmpty()) {
            builder.append("## Limit Rules\n\n");
            builder.append("| Action ID | Currency | Review Limit | Hard Limit |\n");
            builder.append("|---|---:|---:|---:|\n");
            metadata.limitRules().forEach(rule -> builder.append("| ")
                    .append(rule.actionId())
                    .append(" | ")
                    .append(rule.currency())
                    .append(" | ")
                    .append(rule.reviewLimit().toPlainString())
                    .append(" | ")
                    .append(rule.hardLimit().toPlainString())
                    .append(" |\n"));
            builder.append("\n");
        }
        builder.append("## Case Results\n\n");
        builder.append("| Claim ID | Status | Intercepted | Audit Complete | Trace Events | Runtime Ms | Business Action Ms | Framework Ms | Review Wait Ms |\n");
        builder.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (ClaimsPrecheckCaseResult result : metrics.caseResults()) {
            builder.append("| ")
                    .append(result.claimId())
                    .append(" | ")
                    .append(result.status())
                    .append(" | ")
                    .append(result.businessIntercepted())
                    .append(" | ")
                    .append(result.auditComplete())
                    .append(" | ")
                    .append(result.traceEventCount())
                    .append(" | ")
                    .append(String.format(Locale.ROOT, "%.3f", result.elapsedMillis()))
                    .append(" | ")
                    .append(String.format(Locale.ROOT, "%.3f", result.businessActionMillis()))
                    .append(" | ")
                    .append(String.format(Locale.ROOT, "%.3f", result.frameworkMillis()))
                    .append(" | ")
                    .append(String.format(Locale.ROOT, "%.3f", result.reviewWaitMillis()))
                    .append(" |\n");
        }
        return builder.toString();
    }

    private String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private String htmlConsole(ClaimsPrecheckBatchMetrics metrics, ClaimsPrecheckBatchReportMetadata metadata) {
        StringBuilder rows = new StringBuilder();
        for (ClaimsPrecheckCaseResult result : metrics.caseResults()) {
            rows.append("<tr>")
                    .append("<td>").append(escape(result.claimId())).append("</td>")
                    .append("<td><span class=\"status ")
                    .append(statusClass(result))
                    .append("\">")
                    .append(escape(result.status().name()))
                    .append("</span></td>")
                    .append("<td>").append(result.businessIntercepted() ? "Yes" : "No").append("</td>")
                    .append("<td>").append(result.auditComplete() ? "Complete" : "Broken").append("</td>")
                    .append("<td class=\"num\">").append(result.traceEventCount()).append("</td>")
                    .append("<td class=\"num\">").append(formatMillis(result.elapsedMillis())).append("</td>")
                    .append("<td class=\"num\">").append(formatMillis(result.businessActionMillis())).append("</td>")
                    .append("<td class=\"num\">").append(formatMillis(result.frameworkMillis())).append("</td>")
                    .append("<td class=\"num\">").append(formatMillis(result.reviewWaitMillis())).append("</td>")
                    .append("</tr>");
        }

        String generatedAt = Instant.now().toString();
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>ActionGraph Claims Precheck Console</title>
                  <style>
                    :root {
                      color-scheme: light;
                      --bg: #f6f8fb;
                      --panel: #ffffff;
                      --line: #d7dde8;
                      --text: #172033;
                      --muted: #607086;
                      --ok: #16724a;
                      --ok-bg: #e7f6ee;
                      --warn: #9a5b00;
                      --warn-bg: #fff3d8;
                      --bad: #b42318;
                      --bad-bg: #fde8e7;
                      --info: #215bb5;
                      --info-bg: #e8f0ff;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      background: var(--bg);
                      color: var(--text);
                      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                      letter-spacing: 0;
                    }
                    header {
                      border-bottom: 1px solid var(--line);
                      background: var(--panel);
                    }
                    .wrap {
                      width: min(1180px, calc(100%% - 32px));
                      margin: 0 auto;
                    }
                    .top {
                      padding: 24px 0 20px;
                      display: grid;
                      gap: 12px;
                    }
                    h1 {
                      margin: 0;
                      font-size: 28px;
                      line-height: 1.2;
                      font-weight: 750;
                    }
                    .meta {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 8px;
                      color: var(--muted);
                      font-size: 13px;
                    }
                    .chip {
                      border: 1px solid var(--line);
                      background: #fbfcfe;
                      border-radius: 6px;
                      padding: 6px 8px;
                    }
                    main {
                      padding: 24px 0 32px;
                      display: grid;
                      gap: 18px;
                    }
                    .metrics {
                      display: grid;
                      grid-template-columns: repeat(4, minmax(0, 1fr));
                      gap: 12px;
                    }
                    .metric, .panel {
                      background: var(--panel);
                      border: 1px solid var(--line);
                      border-radius: 8px;
                    }
                    .metric {
                      padding: 16px;
                      min-height: 116px;
                      display: grid;
                      align-content: space-between;
                    }
                    .metric span {
                      color: var(--muted);
                      font-size: 13px;
                    }
                    .metric strong {
                      font-size: 30px;
                      line-height: 1;
                    }
                    .split {
                      display: grid;
                      grid-template-columns: 1.1fr 1fr;
                      gap: 12px;
                    }
                    .panel {
                      padding: 16px;
                    }
                    .panel h2 {
                      margin: 0 0 12px;
                      font-size: 16px;
                    }
                    .bar {
                      height: 10px;
                      width: 100%%;
                      background: #e9edf4;
                      border-radius: 6px;
                      overflow: hidden;
                      margin: 8px 0 14px;
                    }
                    .bar > span {
                      display: block;
                      height: 100%%;
                      background: var(--ok);
                    }
                    table {
                      width: 100%%;
                      border-collapse: collapse;
                      table-layout: fixed;
                      font-size: 13px;
                    }
                    th, td {
                      border-bottom: 1px solid var(--line);
                      padding: 10px 8px;
                      text-align: left;
                      vertical-align: middle;
                      overflow-wrap: anywhere;
                    }
                    th {
                      color: var(--muted);
                      font-weight: 650;
                    }
                    .num {
                      text-align: right;
                      font-variant-numeric: tabular-nums;
                    }
                    .status {
                      display: inline-block;
                      border-radius: 6px;
                      padding: 4px 7px;
                      font-size: 12px;
                      font-weight: 700;
                      max-width: 100%%;
                    }
                    .status.ok { color: var(--ok); background: var(--ok-bg); }
                    .status.warn { color: var(--warn); background: var(--warn-bg); }
                    .status.bad { color: var(--bad); background: var(--bad-bg); }
                    .status.info { color: var(--info); background: var(--info-bg); }
                    .note {
                      color: var(--muted);
                      font-size: 13px;
                      line-height: 1.5;
                    }
                    @media (max-width: 840px) {
                      .metrics, .split { grid-template-columns: 1fr; }
                      table { table-layout: auto; }
                    }
                  </style>
                </head>
                <body>
                  <header>
                    <div class="wrap top">
                      <h1>Claims Precheck Read-Only Console</h1>
                      <div class="meta">
                        <span class="chip">Batch: %s</span>
                        <span class="chip">Environment: %s</span>
                        <span class="chip">Review: %s</span>
                        <span class="chip">Generated: %s</span>
                      </div>
                    </div>
                  </header>
                  <main class="wrap">
                    <section class="metrics" aria-label="Batch summary metrics">
                      <div class="metric"><span>Total Runs</span><strong>%d</strong></div>
                      <div class="metric"><span>Completed</span><strong>%d</strong></div>
                      <div class="metric"><span>Intercept Rate</span><strong>%s</strong></div>
                      <div class="metric"><span>Audit Complete</span><strong>%s</strong></div>
                    </section>
                    <section class="split">
                      <div class="panel">
                        <h2>Audit Completeness</h2>
                        <div class="bar" aria-label="Audit completeness rate"><span style="width:%s"></span></div>
                        <p class="note">%d of %d runs have a valid trace hash chain.</p>
                      </div>
                      <div class="panel">
                        <h2>Timing</h2>
                        <table>
                          <tbody>
                            <tr><th>Average Runtime</th><td class="num">%s ms</td></tr>
                            <tr><th>Business Action</th><td class="num">%s ms</td></tr>
                            <tr><th>Framework</th><td class="num">%s ms</td></tr>
                            <tr><th>Review Wait</th><td class="num">%s ms</td></tr>
                          </tbody>
                        </table>
                      </div>
                    </section>
                    <section class="panel">
                      <h2>Case Results</h2>
                      <table>
                        <thead>
                          <tr>
                            <th>Claim ID</th><th>Status</th><th>Intercepted</th><th>Audit</th>
                            <th class="num">Trace</th><th class="num">Runtime</th>
                            <th class="num">Business</th><th class="num">Framework</th><th class="num">Review</th>
                          </tr>
                        </thead>
                        <tbody>
                          %s
                        </tbody>
                      </table>
                    </section>
                    <section class="panel">
                      <h2>Source</h2>
                      <p class="note">Sample source: %s<br>External review inputs: %d<br>Simulated review wait: %d ms</p>
                    </section>
                  </main>
                </body>
                </html>
                """.formatted(
                escape(metadata.batchId()),
                escape(metadata.environment()),
                escape(metadata.reviewMode()),
                escape(generatedAt),
                metrics.totalRuns(),
                metrics.completedRuns(),
                percent(metrics.interceptRate()),
                percent(metrics.auditCompletenessRate()),
                percent(metrics.auditCompletenessRate()),
                metrics.auditCompleteRuns(),
                metrics.totalRuns(),
                formatMillis(metrics.averageRuntimeMillis()),
                formatMillis(metrics.averageBusinessActionMillis()),
                formatMillis(metrics.averageFrameworkMillis()),
                formatMillis(metrics.averageReviewWaitMillis()),
                rows,
                escape(metadata.sampleSource()),
                metadata.externalReviewInputCount(),
                metadata.simulatedReviewWaitMillis()
        );
    }

    private String statusClass(ClaimsPrecheckCaseResult result) {
        return switch (result.status()) {
            case COMPLETED -> "ok";
            case HALTED_UNREACHABLE, SUSPENDED_PENDING_REVIEW -> "warn";
            case DENIED_BY_POLICY, FAILED_COMPENSATED, FAILED_COMPENSATION_INCOMPLETE -> "bad";
        };
    }

    private String formatMillis(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
