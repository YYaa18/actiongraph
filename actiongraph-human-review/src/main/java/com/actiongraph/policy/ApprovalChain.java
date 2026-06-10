package com.actiongraph.policy;

import java.util.List;
import java.util.Objects;

public record ApprovalChain(List<ApprovalStage> stages) {
    private static final ApprovalStage SINGLE_STAGE = new ApprovalStage("review", "reviewer");

    public ApprovalChain {
        stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("approval chain must contain at least one stage");
        }
    }

    public static ApprovalChain single() {
        return new ApprovalChain(List.of(SINGLE_STAGE));
    }
}
