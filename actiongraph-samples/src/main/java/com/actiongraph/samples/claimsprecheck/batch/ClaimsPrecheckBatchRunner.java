package com.actiongraph.samples.claimsprecheck.batch;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.policy.AmountAttributeContributor;
import com.actiongraph.policy.AmountExtractor;
import com.actiongraph.policy.AmountLimitPolicy;
import com.actiongraph.policy.AmountLimitRule;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.HumanReviewTask;
import com.actiongraph.policy.InMemoryHumanReviewRepository;
import com.actiongraph.policy.MonetaryAmount;
import com.actiongraph.policy.RepositoryBackedHumanReviewPolicy;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.InMemorySuspendedRunRepository;
import com.actiongraph.runtime.RunResult;
import com.actiongraph.runtime.RunStatus;
import com.actiongraph.samples.claimsprecheck.ClaimsPrecheckActionFactory;
import com.actiongraph.samples.claimsprecheck.ClaimsPrecheckConditions;
import com.actiongraph.samples.claimsprecheck.ClaimsPrecheckGoals;
import com.actiongraph.samples.claimsprecheck.domain.ClaimId;
import com.actiongraph.samples.claimsprecheck.domain.PayoutApplicationDraft;
import com.actiongraph.samples.claimsprecheck.service.InMemoryClaimApprovalService;
import com.actiongraph.samples.claimsprecheck.service.InMemoryClaimDocumentService;
import com.actiongraph.samples.claimsprecheck.service.InMemoryClaimPrecheckService;
import com.actiongraph.samples.claimsprecheck.service.InMemoryClaimService;
import com.actiongraph.samples.claimsprecheck.service.InMemoryPayoutDraftService;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceChainVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class ClaimsPrecheckBatchRunner {
    private static final ActionId APPROVAL_ACTION_ID = new ActionId("claim.approval.request");

    private final List<AmountLimitRule> limitRules;
    private final ClaimsPrecheckBatchReviewOptions reviewOptions;

    public ClaimsPrecheckBatchRunner() {
        this(defaultLimitRules(), ClaimsPrecheckBatchReviewOptions.autoApprove());
    }

    public ClaimsPrecheckBatchRunner(List<AmountLimitRule> limitRules) {
        this(limitRules, ClaimsPrecheckBatchReviewOptions.autoApprove());
    }

    public ClaimsPrecheckBatchRunner(
            List<AmountLimitRule> limitRules,
            ClaimsPrecheckBatchReviewOptions reviewOptions
    ) {
        this.limitRules = List.copyOf(limitRules);
        this.reviewOptions = reviewOptions == null ? ClaimsPrecheckBatchReviewOptions.autoApprove() : reviewOptions;
    }

    public List<AmountLimitRule> limitRules() {
        return limitRules;
    }

    public ClaimsPrecheckBatchReviewOptions reviewOptions() {
        return reviewOptions;
    }

    public static List<AmountLimitRule> defaultLimitRules() {
        return List.of(new AmountLimitRule(
                APPROVAL_ACTION_ID.value(),
                "CNY",
                new BigDecimal("1000000"),
                new BigDecimal("100000")
        ));
    }

    public ClaimsPrecheckBatchMetrics run(List<ClaimsPrecheckBatchCase> cases) {
        List<ClaimsPrecheckCaseResult> results = new ArrayList<>();
        for (ClaimsPrecheckBatchCase batchCase : cases) {
            results.add(runOne(batchCase));
        }
        return new ClaimsPrecheckBatchMetrics(results);
    }

    public static List<ClaimsPrecheckBatchCase> defaultCases() {
        return List.of(
                ClaimsPrecheckBatchCase.normal("CLM100", "260000"),
                ClaimsPrecheckBatchCase.missingInvoice("CLM101", "180000"),
                ClaimsPrecheckBatchCase.closed("CLM102", "90000"),
                ClaimsPrecheckBatchCase.aboveHardLimit("CLM103", "1200000"),
                ClaimsPrecheckBatchCase.approvalFailure("CLM104", "220000")
        );
    }

    private ClaimsPrecheckCaseResult runOne(ClaimsPrecheckBatchCase batchCase) {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new ClaimId(batchCase.claimId()));
        blackboard.addCondition(ClaimsPrecheckConditions.CLAIM_ID_PRESENT);

        InMemoryPayoutDraftService draftService = new InMemoryPayoutDraftService();
        TimingRecorder timing = new TimingRecorder();
        List<Action> actions = timing.wrapActions(ClaimsPrecheckActionFactory.actions(
                new InMemoryClaimService(batchCase.closed(), batchCase.claimedAmount()),
                new InMemoryClaimDocumentService(batchCase.missingInvoice()),
                new InMemoryClaimPrecheckService(),
                draftService,
                new InMemoryClaimApprovalService(batchCase.approvalFails())
        ));
        DefaultActionRegistry registry = ClaimsPrecheckActionFactory.registry(actions);
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        AmountExtractor amountExtractor = claimPayoutAmountExtractor();
        InMemorySuspendedRunRepository suspendedRuns = new InMemorySuspendedRunRepository();
        InMemoryHumanReviewRepository reviewRepository = new InMemoryHumanReviewRepository();
        GoapExecutor executor = GoapExecutor.builder()
                .policyGuard(new DefaultPolicyGuard(new AmountLimitPolicy(amountExtractor, limitRules)))
                .humanReviewPolicy(timing.wrapHumanReviewPolicy(
                        reviewPolicy(reviewRepository),
                        !reviewOptions.suspendResume()
                ))
                .traceRepository(traceRepository)
                .suspendedRunRepository(suspendedRuns)
                .reviewAttributeContributor(new AmountAttributeContributor(amountExtractor, limitRules))
                .build();

        long start = System.nanoTime();
        RunResult result = executor.run(ClaimsPrecheckGoals.prepareClaimPayoutApplication(),
                blackboard, actions, registry);
        result = resumeApprovedReviews(result, executor, actions, registry, reviewRepository, timing);
        long elapsed = System.nanoTime() - start;

        var traceEvents = traceRepository.findByRun(result.runId());
        boolean auditComplete = new TraceChainVerifier().verify(traceEvents).valid();
        boolean businessIntercepted = batchCase.expectedIntercept()
                && (result.status() == com.actiongraph.runtime.RunStatus.HALTED_UNREACHABLE
                || result.status() == com.actiongraph.runtime.RunStatus.DENIED_BY_POLICY);
        long businessActionNanos = timing.businessActionNanos();
        long reviewWaitNanos = timing.reviewWaitNanos();
        long frameworkNanos = Math.max(0, elapsed - businessActionNanos - reviewWaitNanos);
        return new ClaimsPrecheckCaseResult(
                batchCase.claimId(),
                result.status(),
                businessIntercepted,
                auditComplete,
                elapsed,
                businessActionNanos,
                reviewWaitNanos,
                frameworkNanos,
                result.executedActions().size(),
                traceEvents.size()
        );
    }

    private AmountExtractor claimPayoutAmountExtractor() {
        return (action, blackboard) -> {
            if (!action.id().equals(APPROVAL_ACTION_ID)) {
                return Optional.empty();
            }
            return blackboard.get(PayoutApplicationDraft.class)
                    .map(draft -> new MonetaryAmount(draft.amount(), draft.currency()));
        };
    }

    private HumanReviewPolicy reviewPolicy(InMemoryHumanReviewRepository reviewRepository) {
        if (reviewOptions.suspendResume()) {
            return new RepositoryBackedHumanReviewPolicy(reviewRepository);
        }
        return new AutoApproveHumanReviewPolicy();
    }

    private RunResult resumeApprovedReviews(
            RunResult result,
            GoapExecutor executor,
            List<Action> actions,
            DefaultActionRegistry registry,
            InMemoryHumanReviewRepository reviewRepository,
            TimingRecorder timing
    ) {
        RunResult current = result;
        while (reviewOptions.suspendResume() && current.status() == RunStatus.SUSPENDED_PENDING_REVIEW) {
            String runId = current.runId();
            HumanReviewTask task = reviewRepository.findByRun(runId).stream()
                    .filter(candidate -> candidate.decision() == HumanReviewDecision.PENDING)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No pending review task found for suspended run " + runId));
            waitForSimulatedReview();
            reviewRepository.decideStage(
                    task.runId(),
                    task.actionId(),
                    task.currentStageIndex(),
                    HumanReviewDecision.APPROVED,
                    "batch-simulated-reviewer",
                    "Approved by claims precheck batch simulation"
            );
            HumanReviewTask decidedTask = reviewRepository.find(task.runId(), task.actionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No decided review task found for " + task.runId() + "/" + task.actionId().value()));
            timing.recordReviewTaskWait(task, decidedTask);
            current = executor.resume(current.runId(), actions, registry);
        }
        return current;
    }

    private void waitForSimulatedReview() {
        try {
            if (reviewOptions.simulatedReviewWaitMillis() > 0) {
                Thread.sleep(reviewOptions.simulatedReviewWaitMillis());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating human review wait", ex);
        }
    }

    private static final class TimingRecorder {
        private final AtomicLong businessActionNanos = new AtomicLong();
        private final AtomicLong reviewWaitNanos = new AtomicLong();

        List<Action> wrapActions(List<Action> actions) {
            return actions.stream()
                    .map(action -> (Action) new TimingAction(action, this))
                    .toList();
        }

        HumanReviewPolicy wrapHumanReviewPolicy(HumanReviewPolicy delegate, boolean recordAsReviewWait) {
            return request -> {
                long start = System.nanoTime();
                try {
                    return delegate.review(request);
                } finally {
                    if (recordAsReviewWait) {
                        reviewWaitNanos.addAndGet(System.nanoTime() - start);
                    }
                }
            };
        }

        long businessActionNanos() {
            return businessActionNanos.get();
        }

        long reviewWaitNanos() {
            return reviewWaitNanos.get();
        }

        private void recordBusinessAction(long nanos) {
            businessActionNanos.addAndGet(nanos);
        }

        private void recordReviewWait(long nanos) {
            reviewWaitNanos.addAndGet(nanos);
        }

        private void recordReviewTaskWait(HumanReviewTask beforeDecision, HumanReviewTask afterDecision) {
            long nanos = Duration.between(beforeDecision.updatedAt(), afterDecision.updatedAt()).toNanos();
            if (nanos > 0) {
                recordReviewWait(nanos);
            }
        }
    }

    private record TimingAction(Action delegate, TimingRecorder timing) implements Action {
        @Override
        public ActionId id() {
            return delegate.id();
        }

        @Override
        public Set<Class<?>> inputTypes() {
            return delegate.inputTypes();
        }

        @Override
        public Set<Class<?>> outputTypes() {
            return delegate.outputTypes();
        }

        @Override
        public Set<Condition> preconditions() {
            return delegate.preconditions();
        }

        @Override
        public Set<Condition> effects() {
            return delegate.effects();
        }

        @Override
        public int cost() {
            return delegate.cost();
        }

        @Override
        public ActionRiskLevel riskLevel() {
            return delegate.riskLevel();
        }

        @Override
        public boolean requiresHumanReview() {
            return delegate.requiresHumanReview();
        }

        @Override
        public boolean runtimeGuard(Blackboard blackboard) {
            return delegate.runtimeGuard(blackboard);
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            long start = System.nanoTime();
            try {
                return delegate.execute(context);
            } finally {
                timing.recordBusinessAction(System.nanoTime() - start);
            }
        }

        @Override
        public CompensationResult compensate(ExecutionContext context) {
            long start = System.nanoTime();
            try {
                return delegate.compensate(context);
            } finally {
                timing.recordBusinessAction(System.nanoTime() - start);
            }
        }
    }
}
