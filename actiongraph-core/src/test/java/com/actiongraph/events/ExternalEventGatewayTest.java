package com.actiongraph.events;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.durability.RecoveryPolicy;
import com.actiongraph.durability.RunRecoverer;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.policy.PendingHumanReviewPolicy;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.InMemorySuspendedRunRepository;
import com.actiongraph.runtime.RunResult;
import com.actiongraph.runtime.RunStatus;
import com.actiongraph.runtime.SnapshotState;
import com.actiongraph.runtime.SuspendedRun;
import com.actiongraph.runtime.SuspendedRunRepository;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceChainVerifier;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalEventGatewayTest {
    private static final Condition START = Condition.of("events:START");
    private static final Condition SUBMITTED = Condition.of("events:SUBMITTED");
    private static final Condition EVENT_RECEIVED = Condition.of("events:EVENT_RECEIVED");
    private static final Condition DONE = Condition.of("events:DONE");
    private static final Goal GOAL = new Goal("eventGoal", Set.of(DONE));
    private static final String EVENT_TYPE = "approval.completed";
    private static final String CORRELATION_ID = "APP-1";

    @Test
    void waitingActionSuspendsUntilExternalEventIsDelivered() {
        InMemorySuspendedRunRepository repository = new InMemorySuspendedRunRepository();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        SideEffects sideEffects = new SideEffects();
        List<Action> actions = List.of(
                new SubmitForEventAction(sideEffects, Duration.ofMinutes(5)),
                new CompleteAfterEventAction(sideEffects)
        );
        ActionRegistry registry = registry(actions);
        GoapExecutor executor = executor(repository, traceRepository);

        RunResult suspended = executor.run(GOAL, blackboard(), actions, registry);

        assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED_WAITING_EVENT);
        SuspendedRun snapshot = repository.findByRunId(suspended.runId()).orElseThrow();
        assertThat(snapshot.snapshotState()).isEqualTo(SnapshotState.WAITING_EVENT);
        assertThat(snapshot.eventType()).isEqualTo(EVENT_TYPE);
        assertThat(snapshot.eventCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(snapshot.compensationStack()).extracting(ActionId::value)
                .containsExactly("events.submit");

        DeliveryResult delivery = gateway(executor, repository, actions, registry, new ApprovalEventApplier())
                .deliver(EVENT_TYPE, CORRELATION_ID, new EventPayload("application/json", "{}", SetMap.of("source", "test")));

        assertThat(delivery).isEqualTo(DeliveryResult.RESUMED);
        assertThat(repository.findByRunId(suspended.runId())).isEmpty();
        assertThat(sideEffects.submissions()).isEqualTo(1);
        assertThat(sideEffects.completions()).isEqualTo(1);
        assertThat(traceRepository.findByRun(suspended.runId()))
                .extracting(event -> event.type())
                .contains(
                        TraceEventType.EVENT_WAIT_STARTED,
                        TraceEventType.EVENT_DELIVERED,
                        TraceEventType.RUN_ENDED
                );
        assertThat(new TraceChainVerifier().verify(traceRepository.findByRun(suspended.runId())).valid()).isTrue();
    }

    @Test
    void concurrentDeliveryClaimsWaitingRunOnlyOnce() throws Exception {
        InMemorySuspendedRunRepository repository = new InMemorySuspendedRunRepository();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        SideEffects sideEffects = new SideEffects();
        List<Action> actions = List.of(
                new SubmitForEventAction(sideEffects, Duration.ofMinutes(5)),
                new CompleteAfterEventAction(sideEffects)
        );
        ActionRegistry registry = registry(actions);
        GoapExecutor executor = executor(repository, traceRepository);
        RunResult suspended = executor.run(GOAL, blackboard(), actions, registry);
        BlockingApprovalEventApplier applier = new BlockingApprovalEventApplier();
        ExternalEventGateway gateway = gateway(executor, repository, actions, registry, applier);

        try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<DeliveryResult> first = executorService.submit(() ->
                    gateway.deliver(EVENT_TYPE, CORRELATION_ID, EventPayload.empty()));
            assertThat(applier.awaitEntered()).isTrue();

            DeliveryResult second = gateway.deliver(EVENT_TYPE, CORRELATION_ID, EventPayload.empty());
            applier.release();

            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo(DeliveryResult.RESUMED);
            assertThat(second).isEqualTo(DeliveryResult.ALREADY_HANDLED);
        }

        assertThat(sideEffects.completions()).isEqualTo(1);
        assertThat(gateway.deliver(EVENT_TYPE, CORRELATION_ID, EventPayload.empty()))
                .isEqualTo(DeliveryResult.NOT_FOUND);
        assertThat(repository.findByRunId(suspended.runId())).isEmpty();
    }

    @Test
    void expiredWaitCompensatesSubmittedAction() throws Exception {
        InMemorySuspendedRunRepository repository = new InMemorySuspendedRunRepository();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        SideEffects sideEffects = new SideEffects();
        SubmitForEventAction submit = new SubmitForEventAction(sideEffects, Duration.ofMillis(1));
        ActionRegistry registry = registry(List.of(submit, new CompleteAfterEventAction(sideEffects)));
        GoapExecutor executor = executor(repository, traceRepository);

        RunResult suspended = executor.run(GOAL, blackboard(), registry.all(), registry);
        Thread.sleep(5);
        RunResult timedOut = new EventWaitSweeper(executor, repository, registry.all(), registry)
                .sweepOne(Instant.now())
                .orElseThrow();

        assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED_WAITING_EVENT);
        assertThat(timedOut.status()).isEqualTo(RunStatus.FAILED_COMPENSATED);
        assertThat(sideEffects.submissions()).isEqualTo(1);
        assertThat(sideEffects.voids()).isEqualTo(1);
        assertThat(traceRepository.findByRun(suspended.runId()))
                .extracting(event -> event.type())
                .contains(TraceEventType.EVENT_WAIT_TIMED_OUT, TraceEventType.COMPENSATED);
    }

    @Test
    void missingEventApplierDoesNotClaimWaitingSnapshot() {
        InMemorySuspendedRunRepository repository = new InMemorySuspendedRunRepository();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        SideEffects sideEffects = new SideEffects();
        List<Action> actions = List.of(
                new SubmitForEventAction(sideEffects, Duration.ofMinutes(5)),
                new CompleteAfterEventAction(sideEffects)
        );
        ActionRegistry registry = registry(actions);
        GoapExecutor executor = executor(repository, traceRepository);
        RunResult suspended = executor.run(GOAL, blackboard(), actions, registry);

        DeliveryResult result = new ExternalEventGateway(executor, repository, actions, registry, List.of())
                .deliver(EVENT_TYPE, CORRELATION_ID, EventPayload.empty());

        assertThat(result).isEqualTo(DeliveryResult.APPLIER_MISSING);
        assertThat(repository.findByRunId(suspended.runId()).orElseThrow().snapshotState())
                .isEqualTo(SnapshotState.WAITING_EVENT);
    }

    @Test
    void deliveredEventCanLeadToHumanReviewSuspensionInSameRun() {
        InMemorySuspendedRunRepository repository = new InMemorySuspendedRunRepository();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        SideEffects sideEffects = new SideEffects();
        List<Action> actions = List.of(
                new SubmitForEventAction(sideEffects, Duration.ofMinutes(5)),
                new HighRiskCompleteAction(sideEffects)
        );
        ActionRegistry registry = registry(actions);
        GoapExecutor executor = executor(repository, traceRepository);
        RunResult suspended = executor.run(GOAL, blackboard(), actions, registry);

        DeliveryResult delivery = gateway(executor, repository, actions, registry, new ApprovalEventApplier())
                .deliver(EVENT_TYPE, CORRELATION_ID, EventPayload.empty());

        assertThat(delivery).isEqualTo(DeliveryResult.RESUMED);
        SuspendedRun humanReview = repository.findByRunId(suspended.runId()).orElseThrow();
        assertThat(humanReview.snapshotState()).isEqualTo(SnapshotState.SUSPENDED);
        assertThat(humanReview.pendingActionId()).isEqualTo(new ActionId("events.high-risk-complete"));
        assertThat(traceRepository.findByRun(suspended.runId()))
                .filteredOn(event -> event.type() == TraceEventType.RUN_SUSPENDED)
                .extracting(event -> event.data().get("status"))
                .containsExactly(RunStatus.SUSPENDED_WAITING_EVENT.name(), RunStatus.SUSPENDED_PENDING_REVIEW.name());
        assertThat(new TraceChainVerifier().verify(traceRepository.findByRun(suspended.runId())).valid()).isTrue();
    }

    @Test
    void eventDeliveryFailureBeforeCheckpointRestoresWaitingSnapshotForRedelivery() {
        InMemorySuspendedRunRepository repository = new InMemorySuspendedRunRepository();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        SideEffects sideEffects = new SideEffects();
        List<Action> actions = List.of(
                new SubmitForEventAction(sideEffects, Duration.ofMinutes(5)),
                new CompleteAfterEventAction(sideEffects)
        );
        ActionRegistry registry = registry(actions);
        GoapExecutor executor = executor(repository, traceRepository);
        RunResult suspended = executor.run(GOAL, blackboard(), actions, registry);
        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        EventApplier flakyApplier = new EventApplier() {
            @Override
            public String eventType() {
                return EVENT_TYPE;
            }

            @Override
            public void apply(EventPayload payload, Blackboard blackboard) {
                if (firstAttempt.getAndSet(false)) {
                    throw new IllegalStateException("event parser temporarily unavailable");
                }
                blackboard.addCondition(EVENT_RECEIVED);
            }
        };
        ExternalEventGateway gateway = gateway(executor, repository, actions, registry, flakyApplier);

        assertThatThrownBy(() -> gateway.deliver(EVENT_TYPE, CORRELATION_ID, EventPayload.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("temporarily unavailable");

        assertThat(repository.findByRunId(suspended.runId()).orElseThrow().snapshotState())
                .isEqualTo(SnapshotState.WAITING_EVENT);
        assertThat(gateway.deliver(EVENT_TYPE, CORRELATION_ID, EventPayload.empty()))
                .isEqualTo(DeliveryResult.RESUMED);
        assertThat(sideEffects.completions()).isEqualTo(1);
    }

    @Test
    void crashAfterEventCheckpointIsRecoveredWithoutRedeliveringEvent() {
        InMemorySuspendedRunRepository delegate = new InMemorySuspendedRunRepository();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        SideEffects sideEffects = new SideEffects();
        List<Action> actions = List.of(
                new SubmitForEventAction(sideEffects, Duration.ofMinutes(5)),
                new CompleteAfterEventAction(sideEffects)
        );
        ActionRegistry registry = registry(actions);
        GoapExecutor firstExecutor = executor(delegate, traceRepository);
        RunResult suspended = firstExecutor.run(GOAL, blackboard(), actions, registry);
        KillingCheckpointRepository repository = new KillingCheckpointRepository(delegate);
        GoapExecutor eventExecutor = executor(repository, traceRepository);

        assertThatThrownBy(() -> gateway(eventExecutor, repository, actions, registry, new ApprovalEventApplier())
                .deliver(EVENT_TYPE, CORRELATION_ID, EventPayload.empty()))
                .isInstanceOf(ProcessKilled.class);

        SuspendedRun checkpoint = delegate.findByRunId(suspended.runId()).orElseThrow();
        assertThat(checkpoint.snapshotState()).isEqualTo(SnapshotState.RUNNING);
        delegate.saveCheckpoint(stale(checkpoint));
        assertThat(gateway(eventExecutor, repository, actions, registry, new ApprovalEventApplier())
                .deliver(EVENT_TYPE, CORRELATION_ID, EventPayload.empty()))
                .isEqualTo(DeliveryResult.NOT_FOUND);

        RunRecoverer recoverer = new RunRecoverer(
                executor(delegate, traceRepository),
                delegate,
                actions,
                registry,
                RecoveryPolicy.CONTINUE
        );
        RunResult recovered = recoverer.recoverOne(Instant.now()).orElseThrow();

        assertThat(recovered.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(sideEffects.completions()).isEqualTo(1);
        assertThat(traceRepository.findByRun(suspended.runId()))
                .extracting(event -> event.type())
                .contains(TraceEventType.EVENT_DELIVERED, TraceEventType.RUN_RECOVERED);
    }

    private static GoapExecutor executor(SuspendedRunRepository repository, InMemoryTraceRepository traceRepository) {
        return new GoapExecutor.Builder()
                .traceRepository(traceRepository)
                .suspendedRunRepository(repository)
                .humanReviewPolicy(new PendingHumanReviewPolicy())
                .durabilityEnabled(true)
                .heartbeatInterval(Duration.ofMillis(50))
                .defaultEventWaitTimeout(Duration.ofMinutes(10))
                .build();
    }

    private static ExternalEventGateway gateway(
            GoapExecutor executor,
            SuspendedRunRepository repository,
            List<Action> actions,
            ActionRegistry registry,
            EventApplier applier
    ) {
        return new ExternalEventGateway(executor, repository, actions, registry, List.of(applier));
    }

    private static InMemoryBlackboard blackboard() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.addCondition(START);
        return blackboard;
    }

    private static ActionRegistry registry(List<Action> actions) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        actions.forEach(registry::register);
        return registry;
    }

    private static SuspendedRun stale(SuspendedRun run) {
        return new SuspendedRun(
                run.runId(),
                run.goal(),
                run.blackboard(),
                run.executedActions(),
                run.compensationStack(),
                run.pendingActionId(),
                run.message(),
                run.snapshotState(),
                Instant.EPOCH,
                run.inFlightActionId()
        );
    }

    private static final class SubmitForEventAction implements Action {
        private final SideEffects sideEffects;
        private final Duration timeout;

        private SubmitForEventAction(SideEffects sideEffects, Duration timeout) {
            this.sideEffects = sideEffects;
            this.timeout = timeout;
        }

        @Override
        public ActionId id() {
            return new ActionId("events.submit");
        }

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
            return Set.of(EVENT_RECEIVED);
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
            sideEffects.submit();
            return ActionResult.waiting(EVENT_TYPE, CORRELATION_ID, timeout, "waiting for approval event");
        }

        @Override
        public CompensationResult compensate(ExecutionContext context) {
            sideEffects.voidSubmission();
            return CompensationResult.ok("voided submission");
        }
    }

    private static class CompleteAfterEventAction implements Action {
        private final SideEffects sideEffects;

        private CompleteAfterEventAction(SideEffects sideEffects) {
            this.sideEffects = sideEffects;
        }

        @Override
        public ActionId id() {
            return new ActionId("events.complete");
        }

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
            return Set.of(EVENT_RECEIVED);
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

        @Override
        public ActionResult execute(ExecutionContext context) {
            sideEffects.complete();
            return ActionResult.ok();
        }

        @Override
        public CompensationResult compensate(ExecutionContext context) {
            return CompensationResult.noop();
        }
    }

    private static final class HighRiskCompleteAction extends CompleteAfterEventAction {
        private HighRiskCompleteAction(SideEffects sideEffects) {
            super(sideEffects);
        }

        @Override
        public ActionId id() {
            return new ActionId("events.high-risk-complete");
        }

        @Override
        public ActionRiskLevel riskLevel() {
            return ActionRiskLevel.HIGH;
        }

        @Override
        public boolean requiresHumanReview() {
            return true;
        }
    }

    private static class ApprovalEventApplier implements EventApplier {
        @Override
        public String eventType() {
            return EVENT_TYPE;
        }

        @Override
        public void apply(EventPayload payload, Blackboard blackboard) {
            blackboard.addCondition(EVENT_RECEIVED);
        }
    }

    private static final class BlockingApprovalEventApplier extends ApprovalEventApplier {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void apply(EventPayload payload, Blackboard blackboard) {
            entered.countDown();
            try {
                if (!release.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("not released");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", ex);
            }
            super.apply(payload, blackboard);
        }

        boolean awaitEntered() throws InterruptedException {
            return entered.await(1, TimeUnit.SECONDS);
        }

        void release() {
            release.countDown();
        }
    }

    private static final class SideEffects {
        private final AtomicInteger submissions = new AtomicInteger();
        private final AtomicInteger completions = new AtomicInteger();
        private final AtomicInteger voids = new AtomicInteger();

        void submit() {
            submissions.incrementAndGet();
        }

        void complete() {
            completions.incrementAndGet();
        }

        void voidSubmission() {
            voids.incrementAndGet();
        }

        int submissions() {
            return submissions.get();
        }

        int completions() {
            return completions.get();
        }

        int voids() {
            return voids.get();
        }
    }

    private static final class KillingCheckpointRepository implements SuspendedRunRepository {
        private final InMemorySuspendedRunRepository delegate;
        private final AtomicBoolean killNextCheckpoint = new AtomicBoolean(true);

        private KillingCheckpointRepository(InMemorySuspendedRunRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void save(SuspendedRun run) {
            delegate.save(run);
        }

        @Override
        public void saveCheckpoint(SuspendedRun checkpoint) {
            delegate.saveCheckpoint(checkpoint);
            if (killNextCheckpoint.getAndSet(false)) {
                throw new ProcessKilled();
            }
        }

        @Override
        public Optional<SuspendedRun> findByRunId(String runId) {
            return delegate.findByRunId(runId);
        }

        @Override
        public Optional<SuspendedRun> claimForResume(String runId) {
            return delegate.claimForResume(runId);
        }

        @Override
        public Optional<SuspendedRun> claimWaitingEvent(String eventType, String correlationId) {
            return delegate.claimWaitingEvent(eventType, correlationId);
        }

        @Override
        public Optional<SuspendedRun> claimExpiredWaiting(Instant now) {
            return delegate.claimExpiredWaiting(now);
        }

        @Override
        public boolean markInFlight(String runId, ActionId actionId) {
            return delegate.markInFlight(runId, actionId);
        }

        @Override
        public void heartbeat(String runId) {
            delegate.heartbeat(runId);
        }

        @Override
        public Optional<SuspendedRun> claimStaleRunning(Instant staleBefore) {
            return delegate.claimStaleRunning(staleBefore);
        }

        @Override
        public void delete(String runId) {
            delegate.delete(runId);
        }
    }

    private static final class SetMap {
        private static java.util.Map<String, String> of(String key, String value) {
            return java.util.Map.of(key, value);
        }
    }

    private static final class ProcessKilled extends Error {
        private static final long serialVersionUID = 1L;
    }
}
