package com.actiongraph.samples.claimsprecheck.batch;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClaimsPrecheckBatchMetricsApp {
    private static final Path DEFAULT_REPORT_DIR = Path.of("actiongraph-samples", "build", "reports", "claims-precheck");

    private ClaimsPrecheckBatchMetricsApp() {
    }

    public static void main(String[] args) {
        AppArgs appArgs = AppArgs.parse(args);
        List<ClaimsPrecheckBatchCase> cases = appArgs.loadCases();
        ClaimsPrecheckBatchRunner runner = new ClaimsPrecheckBatchRunner();
        ClaimsPrecheckBatchMetrics metrics = runner.run(cases);
        ClaimsPrecheckBatchReportMetadata metadata = new ClaimsPrecheckBatchReportMetadata(
                appArgs.batchId(),
                appArgs.sampleSource(),
                appArgs.environment(),
                runner.limitRules()
        );
        new ClaimsPrecheckBatchReportWriter().write(appArgs.reportDir(), metrics, metadata);
        System.out.println("claimsPrecheckBatch totalRuns=" + metrics.totalRuns()
                + ", completed=" + metrics.completedRuns()
                + ", intercepted=" + metrics.interceptedRuns()
                + ", failed=" + metrics.failedRuns()
                + ", auditComplete=" + metrics.auditCompleteRuns());
        System.out.println("interceptRate=" + percent(metrics.interceptRate())
                + ", auditCompletenessRate=" + percent(metrics.auditCompletenessRate())
                + ", averageRuntimeMs=" + String.format(Locale.ROOT, "%.3f", metrics.averageRuntimeMillis())
                + ", averageBusinessActionMs="
                + String.format(Locale.ROOT, "%.3f", metrics.averageBusinessActionMillis())
                + ", averageFrameworkMs=" + String.format(Locale.ROOT, "%.3f", metrics.averageFrameworkMillis())
                + ", averageReviewWaitMs=" + String.format(Locale.ROOT, "%.3f", metrics.averageReviewWaitMillis()));
        metrics.caseResults().forEach(result -> System.out.println(
                "case claimId=" + result.claimId()
                        + ", status=" + result.status()
                        + ", intercepted=" + result.businessIntercepted()
                        + ", auditComplete=" + result.auditComplete()
                        + ", traceEvents=" + result.traceEventCount()
                        + ", elapsedMs=" + String.format(Locale.ROOT, "%.3f", result.elapsedMillis())
                        + ", businessActionMs="
                        + String.format(Locale.ROOT, "%.3f", result.businessActionMillis())
                        + ", frameworkMs=" + String.format(Locale.ROOT, "%.3f", result.frameworkMillis())
                        + ", reviewWaitMs=" + String.format(Locale.ROOT, "%.3f", result.reviewWaitMillis())
        ));
        System.out.println("batchId=" + metadata.batchId()
                + ", environment=" + metadata.environment()
                + ", sampleSource=" + metadata.sampleSource());
        System.out.println("reportDir=" + appArgs.reportDir().toAbsolutePath());
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private record AppArgs(
            Path input,
            ClaimsPrecheckBatchJdbcInput jdbcInput,
            Path reportDir,
            String batchId,
            String environment
    ) {
        List<ClaimsPrecheckBatchCase> loadCases() {
            if (input != null) {
                return ClaimsPrecheckBatchCsv.readCases(input);
            }
            if (jdbcInput != null) {
                return ClaimsPrecheckBatchJdbc.readCases(jdbcInput);
            }
            return ClaimsPrecheckBatchRunner.defaultCases();
        }

        String sampleSource() {
            if (input != null) {
                return input.toString();
            }
            if (jdbcInput != null) {
                return jdbcInput.sourceDescription();
            }
            return "built-in-default-cases";
        }

        static AppArgs parse(String[] args) {
            Path input = null;
            String jdbcUrl = null;
            String jdbcUser = "";
            String jdbcPassword = "";
            String jdbcQuery = null;
            Path reportDir = DEFAULT_REPORT_DIR;
            String batchId = "claims-precheck-" + Instant.now().toEpochMilli();
            String environment = System.getenv().getOrDefault("ACTIONGRAPH_ENV", "local");
            List<String> tokens = new ArrayList<>(List.of(args));
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if ("--input".equals(token)) {
                    input = Path.of(nextValue(tokens, ++i, "--input"));
                } else if ("--report-dir".equals(token)) {
                    reportDir = Path.of(nextValue(tokens, ++i, "--report-dir"));
                } else if ("--jdbc-url".equals(token)) {
                    jdbcUrl = nextValue(tokens, ++i, "--jdbc-url");
                } else if ("--jdbc-user".equals(token)) {
                    jdbcUser = nextValue(tokens, ++i, "--jdbc-user");
                } else if ("--jdbc-password".equals(token)) {
                    jdbcPassword = nextValue(tokens, ++i, "--jdbc-password");
                } else if ("--jdbc-query".equals(token)) {
                    jdbcQuery = nextValue(tokens, ++i, "--jdbc-query");
                } else if ("--batch-id".equals(token)) {
                    batchId = nextValue(tokens, ++i, "--batch-id");
                } else if ("--environment".equals(token)) {
                    environment = nextValue(tokens, ++i, "--environment");
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + token);
                }
            }
            if (input != null && jdbcUrl != null) {
                throw new IllegalArgumentException("--input and --jdbc-url are mutually exclusive");
            }
            if (jdbcUrl == null && (!jdbcUser.isBlank() || !jdbcPassword.isBlank() || jdbcQuery != null)) {
                throw new IllegalArgumentException("--jdbc-url is required when JDBC options are provided");
            }
            ClaimsPrecheckBatchJdbcInput jdbcInput = jdbcUrl == null
                    ? null
                    : new ClaimsPrecheckBatchJdbcInput(jdbcUrl, jdbcUser, jdbcPassword, jdbcQuery);
            return new AppArgs(input, jdbcInput, reportDir, batchId, environment);
        }

        private static String nextValue(List<String> tokens, int index, String option) {
            if (index >= tokens.size()) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return tokens.get(index);
        }
    }
}
