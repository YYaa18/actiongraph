package com.actiongraph.samples.claimsprecheck.batch;

import com.actiongraph.policy.AmountLimitRule;

import java.util.List;
import java.util.Objects;

public record ClaimsPrecheckBatchReportMetadata(
        String batchId,
        String sampleSource,
        String environment,
        List<AmountLimitRule> limitRules
) {
    public ClaimsPrecheckBatchReportMetadata {
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("batchId must not be blank");
        }
        if (sampleSource == null || sampleSource.isBlank()) {
            throw new IllegalArgumentException("sampleSource must not be blank");
        }
        if (environment == null || environment.isBlank()) {
            throw new IllegalArgumentException("environment must not be blank");
        }
        limitRules = List.copyOf(Objects.requireNonNull(limitRules, "limitRules"));
    }

    public static ClaimsPrecheckBatchReportMetadata defaults(List<AmountLimitRule> limitRules) {
        return new ClaimsPrecheckBatchReportMetadata(
                "ad-hoc",
                "built-in-default-cases",
                "local",
                limitRules
        );
    }
}
