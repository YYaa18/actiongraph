package com.actiongraph.samples.renewal.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.samples.renewal.RenewalConditions;
import com.actiongraph.samples.renewal.domain.CustomerProfile;
import com.actiongraph.samples.renewal.domain.QuoteDraft;
import com.actiongraph.samples.renewal.domain.RenewalEligibility;
import com.actiongraph.samples.renewal.service.QuoteService;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;

import java.util.Set;

public final class QuoteDraftCreateAction implements Action {
    public static final ActionId ID = new ActionId("quote.draft.create");

    private final QuoteService quoteService;

    public QuoteDraftCreateAction(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(CustomerProfile.class, RenewalEligibility.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(QuoteDraft.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(
                RenewalConditions.CUSTOMER_PROFILE_LOADED,
                RenewalConditions.RENEWAL_ELIGIBILITY_CHECKED
        );
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(RenewalConditions.QUOTE_DRAFT_CREATED);
    }

    @Override
    public int cost() {
        return 1;
    }

    @Override
    public ActionRiskLevel riskLevel() {
        return ActionRiskLevel.MEDIUM;
    }

    @Override
    public boolean requiresHumanReview() {
        return false;
    }

    @Override
    public boolean runtimeGuard(Blackboard blackboard) {
        return blackboard.get(RenewalEligibility.class)
                .map(RenewalEligibility::eligible)
                .orElse(false);
    }

    @Override
    public ActionResult execute(ExecutionContext context) {
        CustomerProfile profile = context.blackboard().get(CustomerProfile.class)
                .orElseThrow(() -> new IllegalStateException("CustomerProfile missing"));
        RenewalEligibility eligibility = context.blackboard().get(RenewalEligibility.class)
                .orElseThrow(() -> new IllegalStateException("RenewalEligibility missing"));
        context.blackboard().put(quoteService.createDraft(profile, eligibility));
        return ActionResult.ok();
    }

    @Override
    public CompensationResult compensate(ExecutionContext context) {
        return context.blackboard().get(QuoteDraft.class)
                .map(draft -> {
                    quoteService.voidDraft(draft.quoteId());
                    return CompensationResult.ok("Voided quote draft " + draft.quoteId());
                })
                .orElseGet(CompensationResult::noop);
    }
}
