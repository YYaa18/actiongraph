package com.actiongraph.runtime;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.samples.renewal.RenewalActionFactory;
import com.actiongraph.samples.renewal.RenewalConditions;
import com.actiongraph.samples.renewal.RenewalGoals;
import com.actiongraph.samples.renewal.domain.ApprovalRequest;
import com.actiongraph.samples.renewal.domain.CustomerId;
import com.actiongraph.samples.renewal.domain.QuoteDraft;
import com.actiongraph.samples.renewal.service.InMemoryApprovalService;
import com.actiongraph.samples.renewal.service.InMemoryContractService;
import com.actiongraph.samples.renewal.service.InMemoryCustomerService;
import com.actiongraph.samples.renewal.service.InMemoryQuoteService;
import com.actiongraph.samples.renewal.service.InMemoryRenewalPolicyService;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.governance.humanreview.AmountAttributeContributor;
import com.actiongraph.governance.AmountExtractor;
import com.actiongraph.governance.AmountLimitPolicy;
import com.actiongraph.governance.AmountLimitRule;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.DenyingHumanReviewPolicy;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.InMemoryHumanReviewRepository;
import com.actiongraph.governance.MonetaryAmount;
import com.actiongraph.policy.PendingHumanReviewPolicy;
import com.actiongraph.policy.PermissionPolicy;
import com.actiongraph.policy.RepositoryBackedHumanReviewPolicy;
import com.actiongraph.governance.humanreview.RiskBasedChainResolver;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyExecutionTest {
    private static final ActionId SALES_APPROVAL_REQUEST = new ActionId("sales.approval.request");

    @Test
    void defaultHumanReviewPolicyStopsBeforeHighRiskAction() {
        Fixture fixture = fixture(new DefaultPolicyGuard(), null);
        GoapExecutor executor = new GoapExecutor(new GoapPlanner(), new DefaultPolicyGuard(), fixture.traceRepository());

        RunResult result = executor.run(RenewalGoals.prepareRenewalQuote(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(executor.suspendedRunRepository().findByRunId(result.runId())).isPresent();
        assertThat(result.executedActions()).extracting(actionId -> actionId.value())
                .containsExactly(
                        "contract.current.query",
                        "customer.profile.query",
                        "renewal.eligibility.check",
                        "quote.draft.create"
                );
        assertThat(fixture.blackboard().get(QuoteDraft.class)).isPresent();
        assertThat(fixture.blackboard().get(ApprovalRequest.class)).isEmpty();
        assertThat(events(fixture, result, TraceEventType.HUMAN_REVIEW_REQUESTED))
                .extracting(TraceEvent::actionId)
                .containsExactly("sales.approval.request");
        assertThat(events(fixture, result, TraceEventType.HUMAN_REVIEW_DECIDED))
                .extracting(event -> event.data().get("decision"))
                .containsExactly("PENDING");
    }

    @Test
    void deniedHumanReviewStopsBeforeExecutingReviewedAction() {
        Fixture fixture = fixture(new DefaultPolicyGuard(), null);
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new DenyingHumanReviewPolicy(),
                fixture.traceRepository()
        );

        RunResult result = executor.run(RenewalGoals.prepareRenewalQuote(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.DENIED_BY_POLICY);
        assertThat(fixture.quoteService().voidedQuoteIds()).containsExactly("QUOTE-1");
        assertThat(fixture.blackboard().get(ApprovalRequest.class)).isEmpty();
        assertThat(events(fixture, result, TraceEventType.HUMAN_REVIEW_DECIDED))
                .extracting(event -> event.data().get("decision"))
                .containsExactly("DENIED");
        assertThat(events(fixture, result, TraceEventType.POLICY_DENIED))
                .extracting(TraceEvent::actionId)
                .containsExactly("sales.approval.request");
    }

    @Test
    void permissionPolicyDenialStopsBeforeRestrictedAction() {
        PermissionPolicy permissionPolicy = new PermissionPolicy() {
            @Override
            public boolean canExecute(Action action, Blackboard blackboard) {
                return !action.id().value().equals("quote.draft.create");
            }
        };
        DefaultPolicyGuard policyGuard = new DefaultPolicyGuard(permissionPolicy);
        Fixture fixture = fixture(policyGuard, null);
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                policyGuard,
                new AutoApproveHumanReviewPolicy(),
                fixture.traceRepository()
        );

        RunResult result = executor.run(RenewalGoals.prepareRenewalQuote(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.DENIED_BY_POLICY);
        assertThat(fixture.blackboard().get(QuoteDraft.class)).isEmpty();
        assertThat(events(fixture, result, TraceEventType.POLICY_EVALUATED))
                .filteredOn(event -> "DENY".equals(event.data().get("decision")))
                .extracting(TraceEvent::actionId)
                .containsExactly("quote.draft.create");
        assertThat(events(fixture, result, TraceEventType.POLICY_DENIED))
                .extracting(TraceEvent::actionId)
                .containsExactly("quote.draft.create");
    }

    @Test
    void suspendedRunCanResumeWithSameRunIdAndComplete() {
        Fixture fixture = fixture(new DefaultPolicyGuard(), null);
        InMemorySuspendedRunRepository suspendedRuns = new InMemorySuspendedRunRepository();
        GoapExecutor suspendingExecutor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new PendingHumanReviewPolicy(),
                fixture.traceRepository(),
                suspendedRuns
        );

        RunResult suspended = suspendingExecutor.run(RenewalGoals.prepareRenewalQuote(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        GoapExecutor approvingExecutor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                fixture.traceRepository(),
                suspendedRuns
        );
        RunResult resumed = approvingExecutor.resume(suspended.runId(), fixture.actions(), fixture.registry());

        assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(resumed.runId()).isEqualTo(suspended.runId());
        assertThat(resumed.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(resumed.executedActions()).extracting(actionId -> actionId.value())
                .containsExactly(
                        "contract.current.query",
                        "customer.profile.query",
                        "renewal.eligibility.check",
                        "quote.draft.create",
                        "sales.approval.request"
                );
        assertThat(fixture.blackboard().get(ApprovalRequest.class)).isPresent();
        assertThat(suspendedRuns.findByRunId(suspended.runId())).isEmpty();
        assertThat(events(fixture, resumed, TraceEventType.RUN_RESUMED)).hasSize(1);
    }

    @Test
    void riskBasedMultiStageReviewSuspendsAgainUntilFinalStageApproves() {
        Fixture fixture = fixture(new DefaultPolicyGuard(), null);
        InMemorySuspendedRunRepository suspendedRuns = new InMemorySuspendedRunRepository();
        InMemoryHumanReviewRepository reviewRepository = new InMemoryHumanReviewRepository();
        RepositoryBackedHumanReviewPolicy reviewPolicy = new RepositoryBackedHumanReviewPolicy(
                reviewRepository,
                new RiskBasedChainResolver()
        );
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                reviewPolicy,
                fixture.traceRepository(),
                suspendedRuns
        );

        RunResult firstSuspension = executor.run(RenewalGoals.prepareRenewalQuote(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(firstSuspension.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(reviewRepository.findPending()).singleElement().satisfies(task -> {
            assertThat(task.stages()).extracting(stage -> stage.name())
                    .containsExactly("checker-review", "authorization");
            assertThat(task.currentStageIndex()).isZero();
        });

        reviewRepository.decideStage(firstSuspension.runId(), SALES_APPROVAL_REQUEST,
                0, HumanReviewDecision.APPROVED, "checker-1", "checker approved");
        RunResult secondSuspension = executor.resume(firstSuspension.runId(), fixture.actions(), fixture.registry());

        assertThat(secondSuspension.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(secondSuspension.runId()).isEqualTo(firstSuspension.runId());
        assertThat(fixture.blackboard().get(ApprovalRequest.class)).isEmpty();
        assertThat(reviewRepository.findPending()).singleElement()
                .satisfies(task -> assertThat(task.currentStageIndex()).isEqualTo(1));

        reviewRepository.decideStage(firstSuspension.runId(), SALES_APPROVAL_REQUEST,
                1, HumanReviewDecision.APPROVED, "authorizer-1", "authorized");
        RunResult completed = executor.resume(firstSuspension.runId(), fixture.actions(), fixture.registry());

        assertThat(completed.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(fixture.approvalService().requests())
                .containsExactly(new ApprovalRequest("APPROVAL-1", "QUOTE-1"));
        assertThat(suspendedRuns.findByRunId(firstSuspension.runId())).isEmpty();
    }

    @Test
    void riskBasedMultiStageReviewDenialCompensatesDraft() {
        Fixture fixture = fixture(new DefaultPolicyGuard(), null);
        InMemorySuspendedRunRepository suspendedRuns = new InMemorySuspendedRunRepository();
        InMemoryHumanReviewRepository reviewRepository = new InMemoryHumanReviewRepository();
        RepositoryBackedHumanReviewPolicy reviewPolicy = new RepositoryBackedHumanReviewPolicy(
                reviewRepository,
                new RiskBasedChainResolver()
        );
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                reviewPolicy,
                fixture.traceRepository(),
                suspendedRuns
        );

        RunResult firstSuspension = executor.run(RenewalGoals.prepareRenewalQuote(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        reviewRepository.decideStage(firstSuspension.runId(), SALES_APPROVAL_REQUEST,
                0, HumanReviewDecision.DENIED, "checker-1", "blocked");
        RunResult denied = executor.resume(firstSuspension.runId(), fixture.actions(), fixture.registry());

        assertThat(denied.status()).isEqualTo(RunStatus.DENIED_BY_POLICY);
        assertThat(fixture.blackboard().get(ApprovalRequest.class)).isEmpty();
        assertThat(fixture.quoteService().voidedQuoteIds()).containsExactly("QUOTE-1");
        assertThat(suspendedRuns.findByRunId(firstSuspension.runId())).isEmpty();
    }

    @Test
    void amountHardLimitDeniesReviewedActionAndCompensatesDraft() {
        Fixture fixture = fixture(new DefaultPolicyGuard(), null);
        AmountExtractor amountExtractor = renewalQuoteAmountExtractor();
        List<AmountLimitRule> rules = List.of(amountRule("sales.approval.request", "100000", "50000"));
        DefaultPolicyGuard policyGuard = new DefaultPolicyGuard(new AmountLimitPolicy(amountExtractor, rules));
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                policyGuard,
                new AutoApproveHumanReviewPolicy(),
                fixture.traceRepository()
        );

        RunResult result = executor.run(RenewalGoals.prepareRenewalQuote(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.DENIED_BY_POLICY);
        assertThat(fixture.blackboard().get(ApprovalRequest.class)).isEmpty();
        assertThat(fixture.quoteService().voidedQuoteIds()).containsExactly("QUOTE-1");
        assertThat(events(fixture, result, TraceEventType.POLICY_DENIED))
                .extracting(TraceEvent::actionId)
                .containsExactly("sales.approval.request");
    }

    @Test
    void amountReviewLimitAddsEscalationAttributesAndApprovalStage() {
        Fixture fixture = fixture(new DefaultPolicyGuard(), null);
        InMemorySuspendedRunRepository suspendedRuns = new InMemorySuspendedRunRepository();
        InMemoryHumanReviewRepository reviewRepository = new InMemoryHumanReviewRepository();
        RepositoryBackedHumanReviewPolicy reviewPolicy = new RepositoryBackedHumanReviewPolicy(
                reviewRepository,
                new RiskBasedChainResolver()
        );
        AmountExtractor amountExtractor = renewalQuoteAmountExtractor();
        List<AmountLimitRule> rules = List.of(amountRule("sales.approval.request", "1000000", "100000"));
        GoapExecutor executor = GoapExecutor.builder()
                .planner(new GoapPlanner())
                .policyGuard(new DefaultPolicyGuard(new AmountLimitPolicy(amountExtractor, rules)))
                .humanReviewPolicy(reviewPolicy)
                .traceRepository(fixture.traceRepository())
                .suspendedRunRepository(suspendedRuns)
                .reviewAttributeContributor(new AmountAttributeContributor(amountExtractor, rules))
                .build();

        RunResult suspended = executor.run(RenewalGoals.prepareRenewalQuote(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(reviewRepository.findPending()).singleElement().satisfies(task -> {
            assertThat(task.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "amount", "120000",
                    "currency", "CNY",
                    "amountEscalated", "true"
            ));
            assertThat(task.stages()).extracting(stage -> stage.name())
                    .containsExactly("checker-review", "authorization", "amount-authorization");
        });
    }

    @Test
    void amountBelowReviewLimitKeepsExistingApprovalChain() {
        Fixture fixture = fixture(new DefaultPolicyGuard(), null);
        InMemoryHumanReviewRepository reviewRepository = new InMemoryHumanReviewRepository();
        RepositoryBackedHumanReviewPolicy reviewPolicy = new RepositoryBackedHumanReviewPolicy(
                reviewRepository,
                new RiskBasedChainResolver()
        );
        AmountExtractor amountExtractor = renewalQuoteAmountExtractor();
        List<AmountLimitRule> rules = List.of(amountRule("sales.approval.request", "1000000", "200000"));
        GoapExecutor executor = GoapExecutor.builder()
                .planner(new GoapPlanner())
                .policyGuard(new DefaultPolicyGuard(new AmountLimitPolicy(amountExtractor, rules)))
                .humanReviewPolicy(reviewPolicy)
                .traceRepository(fixture.traceRepository())
                .reviewAttributeContributor(new AmountAttributeContributor(amountExtractor, rules))
                .build();

        RunResult suspended = executor.run(RenewalGoals.prepareRenewalQuote(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(reviewRepository.findPending()).singleElement().satisfies(task -> {
            assertThat(task.attributes()).isEmpty();
            assertThat(task.stages()).extracting(stage -> stage.name())
                    .containsExactly("checker-review", "authorization");
        });
    }

    @Test
    void resumedRunFailureCompensatesActionsFromBeforeSuspension() {
        Fixture fixture = fixture(new DefaultPolicyGuard(), null, true);
        InMemorySuspendedRunRepository suspendedRuns = new InMemorySuspendedRunRepository();
        GoapExecutor suspendingExecutor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new PendingHumanReviewPolicy(),
                fixture.traceRepository(),
                suspendedRuns
        );

        RunResult suspended = suspendingExecutor.run(RenewalGoals.prepareRenewalQuote(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        GoapExecutor approvingExecutor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                fixture.traceRepository(),
                suspendedRuns
        );
        RunResult failed = approvingExecutor.resume(suspended.runId(), fixture.actions(), fixture.registry());

        assertThat(failed.runId()).isEqualTo(suspended.runId());
        assertThat(failed.status()).isEqualTo(RunStatus.FAILED_COMPENSATED);
        assertThat(fixture.quoteService().voidedQuoteIds()).containsExactly("QUOTE-1");
        assertThat(suspendedRuns.findByRunId(suspended.runId())).isEmpty();
    }

    @Test
    void concurrentResumeOfSameRunClaimsSuspendedSnapshotOnlyOnce() throws InterruptedException {
        Fixture fixture = fixture(new DefaultPolicyGuard(), null);
        InMemorySuspendedRunRepository suspendedRuns = new InMemorySuspendedRunRepository();
        GoapExecutor suspendingExecutor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new PendingHumanReviewPolicy(),
                fixture.traceRepository(),
                suspendedRuns
        );

        RunResult suspended = suspendingExecutor.run(RenewalGoals.prepareRenewalQuote(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        CyclicBarrier start = new CyclicBarrier(2);
        List<RunResult> results = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        Runnable resume = () -> {
            try {
                start.await();
                GoapExecutor executor = new GoapExecutor(
                        new GoapPlanner(),
                        new DefaultPolicyGuard(),
                        new AutoApproveHumanReviewPolicy(),
                        fixture.traceRepository(),
                        suspendedRuns
                );
                results.add(executor.resume(suspended.runId(), fixture.actions(), fixture.registry()));
            } catch (Throwable ex) {
                errors.add(ex);
            }
        };

        Thread first = Thread.startVirtualThread(resume);
        Thread second = Thread.startVirtualThread(resume);
        first.join();
        second.join();

        assertThat(results).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo(RunStatus.COMPLETED));
        assertThat(errors).singleElement()
                .satisfies(error -> {
                    assertThat(error).isInstanceOf(SuspendedRunNotClaimableException.class);
                    assertThat(((SuspendedRunNotClaimableException) error).runId()).isEqualTo(suspended.runId());
                });
        assertThat(fixture.approvalService().requests())
                .containsExactly(new ApprovalRequest("APPROVAL-1", "QUOTE-1"));
        assertThat(events(fixture, suspended, TraceEventType.RUN_RESUMED)).hasSize(1);
    }

    private Fixture fixture(DefaultPolicyGuard policyGuard, InMemoryTraceRepository traceRepository) {
        return fixture(policyGuard, traceRepository, false);
    }

    private Fixture fixture(
            DefaultPolicyGuard policyGuard,
            InMemoryTraceRepository traceRepository,
            boolean approvalFails
    ) {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new CustomerId("C001"));
        blackboard.addCondition(RenewalConditions.CUSTOMER_ID_PRESENT);

        InMemoryQuoteService quoteService = new InMemoryQuoteService();
        InMemoryApprovalService approvalService = new InMemoryApprovalService(approvalFails);
        List<Action> actions = RenewalActionFactory.actions(
                new InMemoryCustomerService(),
                new InMemoryContractService(),
                new InMemoryRenewalPolicyService(),
                quoteService,
                approvalService
        );
        DefaultActionRegistry registry = RenewalActionFactory.registry(actions);
        return new Fixture(
                blackboard,
                actions,
                registry,
                quoteService,
                approvalService,
                traceRepository == null ? new InMemoryTraceRepository() : traceRepository,
                policyGuard
        );
    }

    private List<TraceEvent> events(Fixture fixture, RunResult result, TraceEventType type) {
        return fixture.traceRepository().findByRun(result.runId()).stream()
                .filter(event -> event.type() == type)
                .toList();
    }

    private AmountExtractor renewalQuoteAmountExtractor() {
        return (action, blackboard) -> {
            if (!action.id().equals(SALES_APPROVAL_REQUEST)) {
                return Optional.empty();
            }
            return blackboard.get(QuoteDraft.class)
                    .map(draft -> new MonetaryAmount(draft.premium(), draft.currency()));
        };
    }

    private AmountLimitRule amountRule(String actionId, String hardLimit, String reviewLimit) {
        return new AmountLimitRule(
                actionId,
                "CNY",
                new BigDecimal(hardLimit),
                new BigDecimal(reviewLimit)
        );
    }

    private record Fixture(
            InMemoryBlackboard blackboard,
            List<Action> actions,
            DefaultActionRegistry registry,
            InMemoryQuoteService quoteService,
            InMemoryApprovalService approvalService,
            InMemoryTraceRepository traceRepository,
            DefaultPolicyGuard policyGuard
    ) {
    }
}
