package com.actiongraph.policy;

public final class SingleStageApprovalChainResolver implements ApprovalChainResolver {
    public static final SingleStageApprovalChainResolver INSTANCE = new SingleStageApprovalChainResolver();

    public SingleStageApprovalChainResolver() {
    }

    @Override
    public ApprovalChain resolve(HumanReviewRequest request) {
        return ApprovalChain.single();
    }
}
