package com.actiongraph.samples.claimsprecheck.batch;

import java.util.Locale;

public final class ClaimsPrecheckBatchMetricsApp {
    private ClaimsPrecheckBatchMetricsApp() {
    }

    public static void main(String[] args) {
        ClaimsPrecheckBatchMetrics metrics = new ClaimsPrecheckBatchRunner()
                .run(ClaimsPrecheckBatchRunner.defaultCases());
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
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }
}
