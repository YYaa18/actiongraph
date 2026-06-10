package com.actiongraph.samples.claimsprecheck.narration;

import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.RunResult;
import com.actiongraph.samples.claimsprecheck.domain.ClaimApprovalRequest;
import com.actiongraph.samples.claimsprecheck.domain.ClaimPrecheckResult;
import com.actiongraph.samples.claimsprecheck.domain.PayoutApplicationDraft;

public final class ClaimsPrecheckRunSummarizer {
    public String summarize(RunResult result, Blackboard blackboard) {
        String draft = blackboard.get(PayoutApplicationDraft.class)
                .map(PayoutApplicationDraft::draftId)
                .orElse("none");
        String approval = blackboard.get(ClaimApprovalRequest.class)
                .map(ClaimApprovalRequest::requestId)
                .orElse("none");
        String missing = blackboard.get(ClaimPrecheckResult.class)
                .map(precheck -> precheck.missingDocuments().isEmpty()
                        ? "none"
                        : String.join(",", precheck.missingDocuments()))
                .orElse("not checked");
        return "claimsPrecheckSummary status=" + result.status()
                + ", payoutDraft=" + draft
                + ", approvalRequest=" + approval
                + ", missingDocuments=" + missing;
    }
}
