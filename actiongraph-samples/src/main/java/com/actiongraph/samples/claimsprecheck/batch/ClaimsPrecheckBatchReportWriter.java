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
}
