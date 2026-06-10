package com.actiongraph.policy;

public interface ApprovalChainResolver {
    ApprovalChain resolve(HumanReviewRequest request);
}
