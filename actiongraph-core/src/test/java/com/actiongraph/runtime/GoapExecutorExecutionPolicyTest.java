package com.actiongraph.runtime;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionExecutionPolicy;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GoapExecutorExecutionPolicyTest {
    private static final Condition START = Condition.of("dx", "START");
    private static final Condition DONE = Condition.of("dx", "DONE");
    private static final Goal GOAL = new Goal("demo", Set.of(DONE));

    @Test
    void retriesExplicitlyIdempotentActionUntilSuccess() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.addCondition(START);
        RetryThenSuccessAction action = new RetryThenSuccessAction();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();

        RunResult result = new GoapExecutor.Builder()
                .traceRepository(traceRepository)
                .build()
                .run(GOAL, blackboard, List.of(action), registry(action));

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(action.attempts()).isEqualTo(3);
        assertThat(action.contextAttempts()).containsExactly(1, 2, 3);
        assertThat(traceRepository.findByRun(result.runId()))
                .filteredOn(event -> event.type() == TraceEventType.ACTION_RETRIED)
                .hasSize(2)
                .extracting(event -> event.data().get("nextAttempt"))
                .containsExactly("2", "3");
    }

    @Test
    void timeoutHasUnknownOutcomeIsCompensatedAndNotRetried() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.addCondition(START);
        TimeoutAction action = new TimeoutAction();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();

        RunResult result = new GoapExecutor.Builder()
                .traceRepository(traceRepository)
                .build()
                .run(GOAL, blackboard, List.of(action), registry(action));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED_COMPENSATED);
        assertThat(action.attempts()).isEqualTo(1);
        assertThat(action.compensations()).isEqualTo(1);
        assertThat(traceRepository.findByRun(result.runId()))
                .filteredOn(event -> event.type() == TraceEventType.ACTION_TIMED_OUT)
                .singleElement()
                .satisfies(event -> assertThat(event.data()).containsEntry("outcome", "UNKNOWN"));
        assertThat(traceRepository.findByRun(result.runId()))
                .filteredOn(event -> event.type() == TraceEventType.ACTION_RETRIED)
                .isEmpty();
        assertThat(traceRepository.findByRun(result.runId()))
                .filteredOn(event -> event.type() == TraceEventType.COMPENSATED)
                .extracting(event -> event.actionId())
                .containsExactly("timeout.action");
    }

    private ActionRegistry registry(Action action) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        registry.register(action);
        return registry;
    }

    private abstract static class BaseAction implements Action {
        @Override
        public Set<Class<?>> inputTypes() {
            return Set.of();
        }

        @Override
        public Set<Class<?>> outputTypes() {
            return Set.of();
        }

        @Override
        public Set<Condition> preconditions() {
            return Set.of(START);
        }

        @Override
        public Set<Condition> effects() {
            return Set.of(DONE);
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
    }

    private static final class RetryThenSuccessAction extends BaseAction {
        private final AtomicInteger attempts = new AtomicInteger();
        private final CopyOnWriteArrayList<Integer> contextAttempts = new CopyOnWriteArrayList<>();

        @Override
        public ActionId id() {
            return new ActionId("retry.action");
        }

        @Override
        public ActionExecutionPolicy executionPolicy() {
            return new ActionExecutionPolicy(3, Duration.ZERO, null);
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            contextAttempts.add(context.attempt());
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return ActionResult.ok();
        }

        int attempts() {
            return attempts.get();
        }

        List<Integer> contextAttempts() {
            return contextAttempts;
        }
    }

    private static final class TimeoutAction extends BaseAction {
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger compensations = new AtomicInteger();

        @Override
        public ActionId id() {
            return new ActionId("timeout.action");
        }

        @Override
        public ActionExecutionPolicy executionPolicy() {
            return new ActionExecutionPolicy(3, Duration.ZERO, Duration.ofMillis(20));
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            attempts.incrementAndGet();
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return ActionResult.ok();
        }

        @Override
        public CompensationResult compensate(ExecutionContext context) {
            compensations.incrementAndGet();
            return CompensationResult.noop();
        }

        int attempts() {
            return attempts.get();
        }

        int compensations() {
            return compensations.get();
        }
    }
}
