package com.actiongraph.samples.ordercancellation.narration;

import com.actiongraph.samples.ordercancellation.domain.OperationsApprovalRequest;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.RunResult;

public final class OrderCancellationRunSummarizer {
    public String summarize(RunResult result, Blackboard blackboard) {
        return switch (result.status()) {
            case COMPLETED -> blackboard.get(OperationsApprovalRequest.class)
                    .map(request -> "Result: cancellation approval requested: " + request)
                    .orElse("Result: completed, but no operations approval request was found.");
            case HALTED_UNREACHABLE -> "Result: halted because no reachable plan remained. " + result.message();
            case SUSPENDED_PENDING_REVIEW -> "Result: suspended pending human review. " + result.message();
            case SUSPENDED_WAITING_EVENT -> "Result: suspended waiting for an external event. " + result.message();
            case DENIED_BY_POLICY -> "Result: denied by policy. " + result.message();
            case FAILED_COMPENSATED -> "Result: failed, and all completed actions were compensated. " + result.message();
            case FAILED_COMPENSATION_INCOMPLETE -> "Result: failed, and some compensation failed. " + result.message();
        };
    }
}
