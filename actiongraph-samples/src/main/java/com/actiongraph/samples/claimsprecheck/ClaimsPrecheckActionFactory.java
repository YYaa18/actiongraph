package com.actiongraph.samples.claimsprecheck;

import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.samples.claimsprecheck.actions.ClaimApprovalRequestAction;
import com.actiongraph.samples.claimsprecheck.actions.ClaimDocumentsQueryAction;
import com.actiongraph.samples.claimsprecheck.actions.ClaimLookupAction;
import com.actiongraph.samples.claimsprecheck.actions.ClaimPrecheckEvaluateAction;
import com.actiongraph.samples.claimsprecheck.actions.PayoutDraftCreateAction;
import com.actiongraph.samples.claimsprecheck.service.ClaimApprovalService;
import com.actiongraph.samples.claimsprecheck.service.ClaimDocumentService;
import com.actiongraph.samples.claimsprecheck.service.ClaimPrecheckService;
import com.actiongraph.samples.claimsprecheck.service.ClaimService;
import com.actiongraph.samples.claimsprecheck.service.PayoutDraftService;

import java.util.List;

public final class ClaimsPrecheckActionFactory {
    private ClaimsPrecheckActionFactory() {
    }

    public static List<Action> actions(
            ClaimService claimService,
            ClaimDocumentService documentService,
            ClaimPrecheckService precheckService,
            PayoutDraftService draftService,
            ClaimApprovalService approvalService
    ) {
        return List.of(
                new ClaimLookupAction(claimService),
                new ClaimDocumentsQueryAction(documentService),
                new ClaimPrecheckEvaluateAction(precheckService),
                new PayoutDraftCreateAction(draftService),
                new ClaimApprovalRequestAction(approvalService)
        );
    }

    public static DefaultActionRegistry registry(List<Action> actions) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        actions.forEach(registry::register);
        return registry;
    }
}
