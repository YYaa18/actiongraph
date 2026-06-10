package com.actiongraph.samples.claimsprecheck;

import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.policy.AmountAttributeContributor;
import com.actiongraph.policy.AmountExtractor;
import com.actiongraph.policy.AmountLimitPolicy;
import com.actiongraph.policy.AmountLimitRule;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.DenyingHumanReviewPolicy;
import com.actiongraph.policy.InMemoryHumanReviewRepository;
import com.actiongraph.policy.MonetaryAmount;
import com.actiongraph.policy.RepositoryBackedHumanReviewPolicy;
import com.actiongraph.policy.RiskBasedChainResolver;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.InMemorySuspendedRunRepository;
import com.actiongraph.runtime.RunResult;
import com.actiongraph.runtime.RunStatus;
import com.actiongraph.samples.claimsprecheck.actions.PayoutDraftCreateAction;
import com.actiongraph.samples.claimsprecheck.domain.ClaimApprovalRequest;
import com.actiongraph.samples.claimsprecheck.domain.ClaimId;
import com.actiongraph.samples.claimsprecheck.domain.PayoutApplicationDraft;
import com.actiongraph.samples.claimsprecheck.service.InMemoryClaimApprovalService;
import com.actiongraph.samples.claimsprecheck.service.InMemoryClaimDocumentService;
import com.actiongraph.samples.claimsprecheck.service.InMemoryClaimPrecheckService;
import com.actiongraph.samples.claimsprecheck.service.InMemoryClaimService;
import com.actiongraph.samples.claimsprecheck.service.InMemoryPayoutDraftService;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimsPrecheckFlowTest {
    @Test
    void plannerFindsClaimPayoutPrecheckPlan() {
        Fixture fixture = fixture(false, false, false);

        assertThat(new GoapPlanner().plan(
                ClaimsPrecheckGoals.prepareClaimPayoutApplication(),
                fixture.blackboard().conditions(),
                fixture.actions()
        )).hasValueSatisfying(plan -> assertThat(plan.steps())
                .extracting(step -> step.actionId().value())
                .containsExactly(
                        "claim.lookup",
                        "claim.documents.query",
                        "claim.precheck.evaluate",
                        "claim.payout.draft.create",
                        "claim.approval.request"
                ));
    }

    @Test
    void completeClaimDocumentsFinishWhenHumanReviewIsApproved() {
        Fixture fixture = fixture(false, false, false);
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                fixture.traceRepository()
        );

        RunResult result = executor.run(ClaimsPrecheckGoals.prepareClaimPayoutApplication(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(fixture.blackboard().get(PayoutApplicationDraft.class)).isPresent();
        assertThat(fixture.blackboard().get(ClaimApprovalRequest.class))
                .contains(new ClaimApprovalRequest("CLAIM-APPROVAL-1", "CLAIM-DRAFT-1"));
        assertThat(fixture.traceRepository().findByRun(result.runId()))
                .extracting(event -> event.type())
                .contains(TraceEventType.HUMAN_REVIEW_REQUESTED, TraceEventType.HUMAN_REVIEW_DECIDED);
    }

    @Test
    void defaultHumanReviewPolicySuspendsBeforeClaimApprovalRequest() {
        Fixture fixture = fixture(false, false, false);
        GoapExecutor executor = new GoapExecutor(new GoapPlanner(), new DefaultPolicyGuard(), fixture.traceRepository());

        RunResult result = executor.run(ClaimsPrecheckGoals.prepareClaimPayoutApplication(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(fixture.blackboard().get(ClaimApprovalRequest.class)).isEmpty();
        assertThat(result.executedActions()).extracting(actionId -> actionId.value())
                .containsExactly(
                        "claim.lookup",
                        "claim.documents.query",
                        "claim.precheck.evaluate",
                        "claim.payout.draft.create"
                );
    }

    @Test
    void missingInvoiceStopsBeforeDraftCreation() {
        Fixture fixture = fixture(true, false, false);
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                fixture.traceRepository()
        );

        RunResult result = executor.run(ClaimsPrecheckGoals.prepareClaimPayoutApplication(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.HALTED_UNREACHABLE);
        assertThat(result.message()).contains(PayoutDraftCreateAction.ID.value());
        assertThat(fixture.draftService().drafts()).isEmpty();
        assertThat(fixture.blackboard().get(ClaimApprovalRequest.class)).isEmpty();
    }

    @Test
    void deniedHumanReviewCompensatesPayoutDraft() {
        Fixture fixture = fixture(false, false, false);
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new DenyingHumanReviewPolicy(),
                fixture.traceRepository()
        );

        RunResult result = executor.run(ClaimsPrecheckGoals.prepareClaimPayoutApplication(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.DENIED_BY_POLICY);
        assertThat(fixture.blackboard().get(ClaimApprovalRequest.class)).isEmpty();
        assertThat(fixture.draftService().voidedDraftIds()).containsExactly("CLAIM-DRAFT-1");
    }

    @Test
    void approvalServiceFailureCompensatesPayoutDraft() {
        Fixture fixture = fixture(false, false, true);
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                fixture.traceRepository()
        );

        RunResult result = executor.run(ClaimsPrecheckGoals.prepareClaimPayoutApplication(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.FAILED_COMPENSATED);
        assertThat(fixture.draftService().voidedDraftIds()).containsExactly("CLAIM-DRAFT-1");
    }

    @Test
    void highClaimAmountAddsAmountAuthorizationStage() {
        Fixture fixture = fixture(false, false, false);
        InMemorySuspendedRunRepository suspendedRuns = new InMemorySuspendedRunRepository();
        InMemoryHumanReviewRepository reviewRepository = new InMemoryHumanReviewRepository();
        RepositoryBackedHumanReviewPolicy reviewPolicy = new RepositoryBackedHumanReviewPolicy(
                reviewRepository,
                new RiskBasedChainResolver()
        );
        AmountExtractor amountExtractor = claimPayoutAmountExtractor();
        List<AmountLimitRule> rules = List.of(new AmountLimitRule(
                "claim.approval.request",
                "CNY",
                new BigDecimal("1000000"),
                new BigDecimal("100000")
        ));
        GoapExecutor executor = GoapExecutor.builder()
                .planner(new GoapPlanner())
                .policyGuard(new DefaultPolicyGuard(new AmountLimitPolicy(amountExtractor, rules)))
                .humanReviewPolicy(reviewPolicy)
                .traceRepository(fixture.traceRepository())
                .suspendedRunRepository(suspendedRuns)
                .reviewAttributeContributor(new AmountAttributeContributor(amountExtractor, rules))
                .build();

        RunResult result = executor.run(ClaimsPrecheckGoals.prepareClaimPayoutApplication(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(reviewRepository.findPending()).singleElement().satisfies(task -> {
            assertThat(task.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "amount", "260000",
                    "currency", "CNY",
                    "amountEscalated", "true"
            ));
            assertThat(task.stages()).extracting(stage -> stage.name())
                    .containsExactly("checker-review", "authorization", "amount-authorization");
        });
    }

    private Fixture fixture(boolean missingInvoice, boolean closed, boolean approvalFails) {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new ClaimId("CLM100"));
        blackboard.addCondition(ClaimsPrecheckConditions.CLAIM_ID_PRESENT);

        InMemoryPayoutDraftService draftService = new InMemoryPayoutDraftService();
        InMemoryClaimApprovalService approvalService = new InMemoryClaimApprovalService(approvalFails);
        List<Action> actions = ClaimsPrecheckActionFactory.actions(
                new InMemoryClaimService(closed),
                new InMemoryClaimDocumentService(missingInvoice),
                new InMemoryClaimPrecheckService(),
                draftService,
                approvalService
        );
        return new Fixture(
                blackboard,
                actions,
                ClaimsPrecheckActionFactory.registry(actions),
                draftService,
                approvalService,
                new InMemoryTraceRepository()
        );
    }

    private AmountExtractor claimPayoutAmountExtractor() {
        return (action, blackboard) -> {
            if (!action.id().value().equals("claim.approval.request")) {
                return Optional.empty();
            }
            return blackboard.get(PayoutApplicationDraft.class)
                    .map(draft -> new MonetaryAmount(draft.amount(), draft.currency()));
        };
    }

    private record Fixture(
            InMemoryBlackboard blackboard,
            List<Action> actions,
            DefaultActionRegistry registry,
            InMemoryPayoutDraftService draftService,
            InMemoryClaimApprovalService approvalService,
            InMemoryTraceRepository traceRepository
    ) {
    }
}
