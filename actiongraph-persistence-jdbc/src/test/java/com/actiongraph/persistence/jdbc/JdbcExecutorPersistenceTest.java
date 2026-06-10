package com.actiongraph.persistence.jdbc;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.humanreview.jdbc.JdbcHumanReviewRepository;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.PendingHumanReviewPolicy;
import com.actiongraph.policy.RepositoryBackedHumanReviewPolicy;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunStatus;
import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceChainVerifier;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcExecutorPersistenceTest {
    private static final Condition INPUT_PRESENT = Condition.of("durable-test:INPUT_PRESENT");
    private static final Condition DRAFTED = Condition.of("durable-test:DRAFTED");
    private static final Condition APPROVED = Condition.of("durable-test:APPROVED");

    @Test
    void resumeFromJdbcRestoresBlackboardTraceSequenceAndCompensationStack() {
        DataSource dataSource = JdbcTestDataSources.h2();
        JdbcTraceRepository traceRepository = new JdbcTraceRepository(dataSource);
        JdbcSuspendedRunRepository suspendedRunRepository = new JdbcSuspendedRunRepository(dataSource);

        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new DurableInput("I-1"));
        blackboard.addCondition(INPUT_PRESENT);
        ActionRegistry firstRegistry = registry(new DraftAction(new ArrayList<>()), new FailingApprovalAction());

        GoapExecutor firstExecutor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new PendingHumanReviewPolicy(),
                traceRepository,
                suspendedRunRepository
        );
        var suspended = firstExecutor.run(
                new Goal("durableApproval", Set.of(APPROVED)),
                blackboard,
                firstRegistry.all(),
                firstRegistry
        );

        assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(suspendedRunRepository.findByRunId(suspended.runId())).isPresent();

        List<String> voidedDrafts = new ArrayList<>();
        ActionRegistry secondRegistry = registry(new DraftAction(voidedDrafts), new FailingApprovalAction());
        GoapExecutor secondExecutor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                traceRepository,
                suspendedRunRepository
        );

        var resumed = secondExecutor.resume(suspended.runId(), secondRegistry.all(), secondRegistry);

        assertThat(resumed.status()).isEqualTo(RunStatus.FAILED_COMPENSATED);
        assertThat(voidedDrafts).containsExactly("DRAFT-I-1");
        assertThat(suspendedRunRepository.findByRunId(suspended.runId())).isEmpty();
        assertThat(traceRepository.findByRun(suspended.runId()))
                .extracting(TraceEvent::seq)
                .isSorted();
        assertThat(traceRepository.findByRun(suspended.runId()))
                .extracting(TraceEvent::type)
                .contains(
                        TraceEventType.RUN_STARTED,
                        TraceEventType.RUN_SUSPENDED,
                        TraceEventType.RUN_RESUMED,
                        TraceEventType.ACTION_FAILED,
                        TraceEventType.COMPENSATED,
                        TraceEventType.RUN_ENDED
                );
        assertThat(new TraceChainVerifier().verify(traceRepository.findByRun(suspended.runId())).valid()).isTrue();
    }

    @Test
    void externalHumanReviewDecisionApprovesJdbcResume() {
        DataSource dataSource = JdbcTestDataSources.h2();
        JdbcTraceRepository traceRepository = new JdbcTraceRepository(dataSource);
        JdbcSuspendedRunRepository suspendedRunRepository = new JdbcSuspendedRunRepository(dataSource);
        JdbcHumanReviewRepository humanReviewRepository = new JdbcHumanReviewRepository(dataSource);

        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new DurableInput("I-2"));
        blackboard.addCondition(INPUT_PRESENT);
        ActionRegistry registry = registry(new DraftAction(new ArrayList<>()), new SuccessfulApprovalAction());
        RepositoryBackedHumanReviewPolicy reviewPolicy = new RepositoryBackedHumanReviewPolicy(humanReviewRepository);

        GoapExecutor firstExecutor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                reviewPolicy,
                traceRepository,
                suspendedRunRepository
        );

        var suspended = firstExecutor.run(
                new Goal("durableApproval", Set.of(APPROVED)),
                blackboard,
                registry.all(),
                registry
        );

        assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(humanReviewRepository.findPending()).singleElement()
                .satisfies(task -> assertThat(task.actionId()).isEqualTo(new ActionId("durable-test.approve")));

        humanReviewRepository.decide(
                suspended.runId(),
                new ActionId("durable-test.approve"),
                HumanReviewDecision.APPROVED,
                "ops-lead",
                "approved externally"
        );

        GoapExecutor secondExecutor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new RepositoryBackedHumanReviewPolicy(humanReviewRepository),
                traceRepository,
                suspendedRunRepository
        );
        var resumed = secondExecutor.resume(suspended.runId(), registry.all(), registry);

        assertThat(resumed.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(suspendedRunRepository.findByRunId(suspended.runId())).isEmpty();
        assertThat(humanReviewRepository.findByRun(suspended.runId())).singleElement().satisfies(task -> {
            assertThat(task.decision()).isEqualTo(HumanReviewDecision.APPROVED);
            assertThat(task.reviewer()).isEqualTo("ops-lead");
        });
    }

    private static ActionRegistry registry(Action... actions) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        for (Action action : actions) {
            registry.register(action);
        }
        return registry;
    }

    private static final class DraftAction implements Action {
        private final List<String> voidedDrafts;

        private DraftAction(List<String> voidedDrafts) {
            this.voidedDrafts = voidedDrafts;
        }

        @Override
        public ActionId id() {
            return new ActionId("durable-test.draft");
        }

        @Override
        public Set<Class<?>> inputTypes() {
            return Set.of(DurableInput.class);
        }

        @Override
        public Set<Class<?>> outputTypes() {
            return Set.of(DurableDraft.class);
        }

        @Override
        public Set<Condition> preconditions() {
            return Set.of(INPUT_PRESENT);
        }

        @Override
        public Set<Condition> effects() {
            return Set.of(DRAFTED);
        }

        @Override
        public int cost() {
            return 1;
        }

        @Override
        public ActionRiskLevel riskLevel() {
            return ActionRiskLevel.LOW;
        }

        @Override
        public boolean requiresHumanReview() {
            return false;
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            DurableInput input = context.blackboard().get(DurableInput.class).orElseThrow();
            context.blackboard().put(new DurableDraft("DRAFT-" + input.value()));
            return ActionResult.ok();
        }

        @Override
        public CompensationResult compensate(ExecutionContext context) {
            DurableDraft draft = context.blackboard().get(DurableDraft.class).orElseThrow();
            voidedDrafts.add(draft.id());
            return CompensationResult.ok("voided " + draft.id());
        }
    }

    private static final class FailingApprovalAction implements Action {
        @Override
        public ActionId id() {
            return new ActionId("durable-test.approve");
        }

        @Override
        public Set<Class<?>> inputTypes() {
            return Set.of(DurableDraft.class);
        }

        @Override
        public Set<Class<?>> outputTypes() {
            return Set.of();
        }

        @Override
        public Set<Condition> preconditions() {
            return Set.of(DRAFTED);
        }

        @Override
        public Set<Condition> effects() {
            return Set.of(APPROVED);
        }

        @Override
        public int cost() {
            return 1;
        }

        @Override
        public ActionRiskLevel riskLevel() {
            return ActionRiskLevel.HIGH;
        }

        @Override
        public boolean requiresHumanReview() {
            return true;
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            return ActionResult.fail("approval system unavailable");
        }
    }

    private static final class SuccessfulApprovalAction implements Action {
        @Override
        public ActionId id() {
            return new ActionId("durable-test.approve");
        }

        @Override
        public Set<Class<?>> inputTypes() {
            return Set.of(DurableDraft.class);
        }

        @Override
        public Set<Class<?>> outputTypes() {
            return Set.of();
        }

        @Override
        public Set<Condition> preconditions() {
            return Set.of(DRAFTED);
        }

        @Override
        public Set<Condition> effects() {
            return Set.of(APPROVED);
        }

        @Override
        public int cost() {
            return 1;
        }

        @Override
        public ActionRiskLevel riskLevel() {
            return ActionRiskLevel.HIGH;
        }

        @Override
        public boolean requiresHumanReview() {
            return true;
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            return ActionResult.ok();
        }
    }

    public record DurableInput(String value) {
    }

    public record DurableDraft(String id) {
    }
}
