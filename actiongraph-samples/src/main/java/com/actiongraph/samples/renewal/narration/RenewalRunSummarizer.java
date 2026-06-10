package com.actiongraph.samples.renewal.narration;

import com.actiongraph.samples.renewal.domain.ApprovalRequest;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.RunResult;

public final class RenewalRunSummarizer {
    public String summarize(RunResult result, Blackboard blackboard) {
        return switch (result.status()) {
            case COMPLETED -> blackboard.get(ApprovalRequest.class)
                    .map(request -> "Result: renewal quote prepared and approval requested: " + request)
                    .orElse("Result: completed, but no approval request was found on the blackboard.");
            case HALTED_UNREACHABLE -> "Result: halted because no reachable plan remained. " + result.message();
            case SUSPENDED_PENDING_REVIEW -> "Result: suspended pending human review. " + result.message();
            case DENIED_BY_POLICY -> "Result: denied by policy. " + result.message();
            case FAILED_COMPENSATED -> "Result: failed, and all completed actions were compensated. " + result.message();
            case FAILED_COMPENSATION_INCOMPLETE -> "Result: failed, and some compensation failed. " + result.message();
        };
    }
}
