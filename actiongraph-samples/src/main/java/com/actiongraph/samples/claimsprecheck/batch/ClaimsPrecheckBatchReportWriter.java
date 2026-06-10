package com.actiongraph.samples.claimsprecheck.batch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public final class ClaimsPrecheckBatchReportWriter {
    public static final String MARKDOWN_REPORT = "claims-precheck-report.md";
    public static final String CSV_RESULTS = "claims-precheck-results.csv";

    public void write(Path reportDir, ClaimsPrecheckBatchMetrics metrics) {
        Objects.requireNonNull(reportDir, "reportDir");
        Objects.requireNonNull(metrics, "metrics");
        try {
            Files.createDirectories(reportDir);
            Files.writeString(reportDir.resolve(MARKDOWN_REPORT), markdown(metrics), StandardCharsets.UTF_8);
            ClaimsPrecheckBatchCsv.writeResults(reportDir.resolve(CSV_RESULTS), metrics);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot write claims precheck report to " + reportDir, ex);
        }
    }

    private String markdown(ClaimsPrecheckBatchMetrics metrics) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Claims Precheck Batch Metrics\n\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Total Runs: ").append(metrics.totalRuns()).append("\n");
        builder.append("- Completed: ").append(metrics.completedRuns()).append("\n");
        builder.append("- Intercepted: ").append(metrics.interceptedRuns()).append("\n");
        builder.append("- Failed: ").append(metrics.failedRuns()).append("\n");
        builder.append("- Audit Complete: ").append(metrics.auditCompleteRuns()).append("\n");
        builder.append("- Intercept Rate: ").append(percent(metrics.interceptRate())).append("\n");
        builder.append("- Audit Completeness Rate: ").append(percent(metrics.auditCompletenessRate())).append("\n");
        builder.append("- Average Runtime Ms: ")
                .append(String.format(Locale.ROOT, "%.3f", metrics.averageRuntimeMillis()))
                .append("\n\n");
        builder.append("| Claim ID | Status | Intercepted | Audit Complete | Trace Events | Runtime Ms |\n");
        builder.append("|---|---:|---:|---:|---:|---:|\n");
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
                    .append(" |\n");
        }
        return builder.toString();
    }

    private String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }
}
