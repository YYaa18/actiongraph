package com.actiongraph.samples.claimsprecheck.batch;

import java.util.List;

public record ClaimsPrecheckBatchMetrics(List<ClaimsPrecheckCaseResult> caseResults) {
    public ClaimsPrecheckBatchMetrics {
        caseResults = List.copyOf(caseResults);
    }

    public int totalRuns() {
        return caseResults.size();
    }

    public long completedRuns() {
        return caseResults.stream()
                .filter(result -> result.status() == com.actiongraph.runtime.RunStatus.COMPLETED)
                .count();
    }

    public long interceptedRuns() {
        return caseResults.stream()
                .filter(ClaimsPrecheckCaseResult::businessIntercepted)
                .count();
    }

    public long failedRuns() {
        return caseResults.stream()
                .filter(result -> result.status() == com.actiongraph.runtime.RunStatus.FAILED_COMPENSATED
                        || result.status() == com.actiongraph.runtime.RunStatus.FAILED_COMPENSATION_INCOMPLETE)
                .count();
    }

    public long auditCompleteRuns() {
        return caseResults.stream()
                .filter(ClaimsPrecheckCaseResult::auditComplete)
                .count();
    }

    public double averageRuntimeMillis() {
        return caseResults.stream()
                .mapToDouble(ClaimsPrecheckCaseResult::elapsedMillis)
                .average()
                .orElse(0.0);
    }

    public double interceptRate() {
        return rate(interceptedRuns());
    }

    public double auditCompletenessRate() {
        return rate(auditCompleteRuns());
    }

    private double rate(long count) {
        return totalRuns() == 0 ? 0.0 : (count * 100.0) / totalRuns();
    }
}
