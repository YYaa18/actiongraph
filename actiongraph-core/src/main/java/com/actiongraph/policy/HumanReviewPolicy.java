package com.actiongraph.policy;

public interface HumanReviewPolicy {
    HumanReviewResult review(HumanReviewRequest request);
}
