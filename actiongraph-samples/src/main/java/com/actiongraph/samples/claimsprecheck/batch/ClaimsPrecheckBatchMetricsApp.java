package com.actiongraph.samples.claimsprecheck.batch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClaimsPrecheckBatchMetricsApp {
    private static final Path DEFAULT_REPORT_DIR = Path.of("actiongraph-samples", "build", "reports", "claims-precheck");

    private ClaimsPrecheckBatchMetricsApp() {
    }

    public static void main(String[] args) {
        AppArgs appArgs = AppArgs.parse(args);
        List<ClaimsPrecheckBatchCase> cases = appArgs.input() == null
                ? ClaimsPrecheckBatchRunner.defaultCases()
                : ClaimsPrecheckBatchCsv.readCases(appArgs.input());
        ClaimsPrecheckBatchMetrics metrics = new ClaimsPrecheckBatchRunner()
                .run(cases);
        new ClaimsPrecheckBatchReportWriter().write(appArgs.reportDir(), metrics);
        System.out.println("claimsPrecheckBatch totalRuns=" + metrics.totalRuns()
                + ", completed=" + metrics.completedRuns()
                + ", intercepted=" + metrics.interceptedRuns()
                + ", failed=" + metrics.failedRuns()
                + ", auditComplete=" + metrics.auditCompleteRuns());
        System.out.println("interceptRate=" + percent(metrics.interceptRate())
                + ", auditCompletenessRate=" + percent(metrics.auditCompletenessRate())
                + ", averageRuntimeMs=" + String.format(Locale.ROOT, "%.3f", metrics.averageRuntimeMillis()));
        metrics.caseResults().forEach(result -> System.out.println(
                "case claimId=" + result.claimId()
                        + ", status=" + result.status()
                        + ", intercepted=" + result.businessIntercepted()
                        + ", auditComplete=" + result.auditComplete()
                        + ", traceEvents=" + result.traceEventCount()
                        + ", elapsedMs=" + String.format(Locale.ROOT, "%.3f", result.elapsedMillis())
        ));
        System.out.println("reportDir=" + appArgs.reportDir().toAbsolutePath());
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private record AppArgs(Path input, Path reportDir) {
        static AppArgs parse(String[] args) {
            Path input = null;
            Path reportDir = DEFAULT_REPORT_DIR;
            List<String> tokens = new ArrayList<>(List.of(args));
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if ("--input".equals(token)) {
                    input = Path.of(nextValue(tokens, ++i, "--input"));
                } else if ("--report-dir".equals(token)) {
                    reportDir = Path.of(nextValue(tokens, ++i, "--report-dir"));
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + token);
                }
            }
            return new AppArgs(input, reportDir);
        }

        private static String nextValue(List<String> tokens, int index, String option) {
            if (index >= tokens.size()) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return tokens.get(index);
        }
    }
}
