package com.actiongraph.samples.claimsprecheck.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.samples.claimsprecheck.ClaimsPrecheckConditions;
import com.actiongraph.samples.claimsprecheck.domain.ClaimDocumentBundle;
import com.actiongraph.samples.claimsprecheck.domain.ClaimRecord;
import com.actiongraph.samples.claimsprecheck.service.ClaimDocumentService;

import java.util.Set;

public final class ClaimDocumentsQueryAction implements Action {
    public static final ActionId ID = new ActionId("claim.documents.query");

    private final ClaimDocumentService documentService;

    public ClaimDocumentsQueryAction(ClaimDocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(ClaimRecord.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(ClaimDocumentBundle.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(ClaimsPrecheckConditions.CLAIM_LOADED);
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(ClaimsPrecheckConditions.DOCUMENTS_LOADED);
    }

    @Override
    public int cost() {
        return 1;
    }

    @Override
    public ActionRiskLevel riskLevel() {
        return ActionRiskLevel.READ_ONLY;
    }

    @Override
    public boolean requiresHumanReview() {
        return false;
    }

    @Override
    public ActionResult execute(ExecutionContext context) {
        ClaimRecord claim = context.blackboard().get(ClaimRecord.class)
                .orElseThrow(() -> new IllegalStateException("ClaimRecord missing"));
        context.blackboard().put(documentService.documentsFor(claim));
        return ActionResult.ok();
    }
}
