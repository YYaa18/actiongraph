package com.actiongraph.runtime;

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
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceChainVerifier;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoapExecutorDurabilityTest {
    private static final Condition START = Condition.of("ms1", "START");
    private static final Condition C1 = Condition.of("ms1", "C1");
    private static final Condition C2 = Condition.of("ms1", "C2");
    private static final Condition C3 = Condition.of("ms1", "C3");
    private static final Condition C4 = Condition.of("ms1", "C4");
    private static final Condition DONE = Condition.of("ms1", "DONE");
    private static final Goal GOAL = new Goal("durableGoal", Set.of(DONE));

    @Test
    void recovererContinuesFromStaleRunningCheckpointAfterProcessDeath() {
        SideEffects sideEffects = new SideEffects();
        List<Action> actions = List.of(
                new StepAction("step.1", START, C1, sideEffects),
                new StepAction("step.2", C1, C2, sideEffects),
                new StepAction("step.3", C2, C3, sideEffects),
                new StepAction("step.4", C3, C4, sideEffects),
                new StepAction("step.5", C4, DONE, sideEffects)
        );
        ActionRegistry registry = registry(actions);
        KillingSuspendedRunRepository repository = new KillingSuspendedRunRepository(4);
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();

        assertThatThrownBy(() -> executor(repository, traceRepository)
                .run(GOAL, blackboard(START), actions, registry))
                .isInstanceOf(ProcessKilled.class);

        String runId = repository.lastCheckpointRunId();
        SuspendedRun checkpoint = repository.findByRunId(runId).orElseThrow();
        assertThat(checkpoint.snapshotState()).isEqualTo(SnapshotState.RUNNING);
        assertThat(checkpoint.executedActions()).extracting(ActionId::value)
                .containsExactly("step.1", "step.2", "step.3");
        repository.saveCheckpoint(stale(checkpoint));

        RunRecoverer recoverer = new RunRecoverer(
                executor(repository, traceRepository),
                repository,
                actions,
                registry,
                RecoveryPolicy.CONTINUE
        );
        RunResult recovered = recoverer.recoverOne(Instant.now()).orElseThrow();

        assertThat(recovered.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(recovered.executedActions()).extracting(ActionId::value)
                .containsExactly("step.1", "step.2", "step.3", "step.4", "step.5");
        assertThat(sideEffects.created("step.1")).isEqualTo(1);
        assertThat(sideEffects.created("step.2")).isEqualTo(1);
        assertThat(sideEffects.created("step.3")).isEqualTo(1);
        assertThat(sideEffects.created("step.4")).isEqualTo(1);
        assertThat(sideEffects.created("step.5")).isEqualTo(1);
        assertThat(traceRepository.findByRun(runId))
                .extracting(event -> event.type())
                .contains(TraceEventType.RUN_RECOVERED, TraceEventType.RUN_CHECKPOINTED);
        assertThat(new TraceChainVerifier().verify(traceRepository.findByRun(runId)).valid()).isTrue();
    }

    @Test
    void recoveryCompensatesUnknownInFlightActionBeforeRedoingIt() {
        SideEffects sideEffects = new SideEffects();
        StepAction draft = new StepAction("draft.create", START, DONE, sideEffects);
        sideEffects.markActive("draft.create");
        InMemorySuspendedRunRepository repository = new InMemorySuspendedRunRepository();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        repository.saveCheckpoint(new SuspendedRun(
                "RUN-INFLIGHT",
                GOAL,
                blackboard(START),
                List.of(),
                List.of(),
                null,
                "in-flight",
                SnapshotState.RUNNING,
                Instant.EPOCH,
                draft.id()
        ));

        RunRecoverer recoverer = new RunRecoverer(
                executor(repository, traceRepository),
                repository,
                List.of(draft),
                registry(List.of(draft)),
                RecoveryPolicy.CONTINUE
        );
        RunResult recovered = recoverer.recoverOne(Instant.now()).orElseThrow();

        assertThat(recovered.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(sideEffects.voided("draft.create")).isEqualTo(1);
        assertThat(sideEffects.active("draft.create")).isEqualTo(1);
        assertThat(traceRepository.findByRun("RUN-INFLIGHT"))
                .filteredOn(event -> event.type() == TraceEventType.COMPENSATED)
                .singleElement()
                .satisfies(event -> assertThat(event.data()).containsEntry("recoveredInFlight", "true"));
    }

    @Test
    void compensatePolicyCompensatesCheckpointStackAndStops() {
        SideEffects sideEffects = new SideEffects();
        StepAction first = new StepAction("step.1", START, C1, sideEffects);
        StepAction second = new StepAction("step.2", C1, C2, sideEffects);
        sideEffects.markActive("step.1");
        sideEffects.markActive("step.2");
        InMemorySuspendedRunRepository repository = new InMemorySuspendedRunRepository();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        repository.saveCheckpoint(new SuspendedRun(
                "RUN-COMPENSATE",
                GOAL,
                blackboard(C2),
                List.of(first.id(), second.id()),
                List.of(second.id(), first.id()),
                null,
                "running",
                SnapshotState.RUNNING,
                Instant.EPOCH,
                null
        ));

        RunRecoverer recoverer = new RunRecoverer(
                executor(repository, traceRepository),
                repository,
                List.of(first, second),
                registry(List.of(first, second)),
                RecoveryPolicy.COMPENSATE
        );
        RunResult recovered = recoverer.recoverOne(Instant.now()).orElseThrow();

        assertThat(recovered.status()).isEqualTo(RunStatus.FAILED_COMPENSATED);
        assertThat(sideEffects.active("step.1")).isZero();
        assertThat(sideEffects.active("step.2")).isZero();
        assertThat(traceRepository.findByRun("RUN-COMPENSATE"))
                .filteredOn(event -> event.type() == TraceEventType.COMPENSATED)
                .extracting(event -> event.actionId())
                .containsExactly("step.2", "step.1");
    }

    @Test
    void concurrentRecoverersClaimStaleCheckpointOnlyOnce() throws Exception {
        SideEffects sideEffects = new SideEffects();
        StepAction action = new StepAction("step.1", START, DONE, sideEffects);
        InMemorySuspendedRunRepository repository = new InMemorySuspendedRunRepository();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        repository.saveCheckpoint(new SuspendedRun(
                "RUN-RACE",
                GOAL,
                blackboard(START),
                List.of(),
                List.of(),
                null,
                "running",
                SnapshotState.RUNNING,
                Instant.EPOCH,
                null
        ));
        ActionRegistry registry = registry(List.of(action));
        RunRecoverer first = new RunRecoverer(
                executor(repository, traceRepository), repository, List.of(action), registry, RecoveryPolicy.CONTINUE);
        RunRecoverer second = new RunRecoverer(
                executor(repository, traceRepository), repository, List.of(action), registry, RecoveryPolicy.CONTINUE);

        try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Optional<RunResult>> firstResult = executorService.submit(() -> first.recoverOne(Instant.now()));
            Future<Optional<RunResult>> secondResult = executorService.submit(() -> second.recoverOne(Instant.now()));

            long present = List.of(firstResult.get(), secondResult.get()).stream()
                    .filter(Optional::isPresent)
                    .count();

            assertThat(present).isEqualTo(1);
            assertThat(sideEffects.created("step.1")).isEqualTo(1);
        }
    }

    @Test
    void heartbeatPreventsRunningCheckpointFromBeingClaimedAsStale() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BlockingAction action = new BlockingAction(started, release);
        RecordingSuspendedRunRepository repository = new RecordingSuspendedRunRepository();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();

        Future<RunResult> future;
        try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            future = executorService.submit(() -> new GoapExecutor.Builder()
                    .suspendedRunRepository(repository)
                    .traceRepository(traceRepository)
                    .durabilityEnabled(true)
                    .heartbeatInterval(Duration.ofMillis(10))
                    .build()
                    .run(GOAL, blackboard(START), List.of(action), registry(List.of(action))));

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            String runId = repository.awaitRunId();
            Instant firstHeartbeat = repository.findByRunId(runId).orElseThrow().heartbeatAt();
            repository.awaitHeartbeatAfter(runId, firstHeartbeat);

            assertThat(repository.claimStaleRunning(firstHeartbeat.plusMillis(1))).isEmpty();

            release.countDown();
            assertThat(future.get(1, TimeUnit.SECONDS).status()).isEqualTo(RunStatus.COMPLETED);
        }
    }

    private GoapExecutor executor(SuspendedRunRepository repository, InMemoryTraceRepository traceRepository) {
        return new GoapExecutor.Builder()
                .suspendedRunRepository(repository)
                .traceRepository(traceRepository)
                .durabilityEnabled(true)
                .heartbeatInterval(Duration.ofMillis(50))
                .build();
    }

    private static InMemoryBlackboard blackboard(Condition condition) {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.addCondition(condition);
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

    private static class StepAction implements Action {
        private final ActionId id;
        private final Condition precondition;
        private final Condition effect;
        private final SideEffects sideEffects;

        private StepAction(String id, Condition precondition, Condition effect, SideEffects sideEffects) {
            this.id = new ActionId(id);
            this.precondition = precondition;
            this.effect = effect;
            this.sideEffects = sideEffects;
        }

        @Override
        public ActionId id() {
            return id;
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
            return Set.of(precondition);
        }

        @Override
        public Set<Condition> effects() {
            return Set.of(effect);
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
            sideEffects.create(id.value());
            return ActionResult.ok();
        }

        @Override
        public CompensationResult compensate(ExecutionContext context) {
            sideEffects.voidOne(id.value());
            return CompensationResult.ok("voided " + id.value());
        }
    }

    private static final class BlockingAction extends StepAction {
        private final CountDownLatch started;
        private final CountDownLatch release;

        private BlockingAction(CountDownLatch started, CountDownLatch release) {
            super("blocking.step", START, DONE, new SideEffects());
            this.started = started;
            this.release = release;
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            started.countDown();
            try {
                if (!release.await(1, TimeUnit.SECONDS)) {
                    return ActionResult.fail("not released");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return ActionResult.fail("interrupted");
            }
            return ActionResult.ok();
        }
    }

    private static final class SideEffects {
        private final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> created = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> active = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> voided = new java.util.concurrent.ConcurrentHashMap<>();

        void create(String id) {
            created.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
            active.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
        }

        void markActive(String id) {
            active.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
        }

        void voidOne(String id) {
            voided.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
            active.computeIfAbsent(id, ignored -> new AtomicInteger()).updateAndGet(value -> Math.max(0, value - 1));
        }

        int created(String id) {
            return created.getOrDefault(id, new AtomicInteger()).get();
        }

        int active(String id) {
            return active.getOrDefault(id, new AtomicInteger()).get();
        }

        int voided(String id) {
            return voided.getOrDefault(id, new AtomicInteger()).get();
        }
    }

    private static final class KillingSuspendedRunRepository implements SuspendedRunRepository {
        private final InMemorySuspendedRunRepository delegate = new InMemorySuspendedRunRepository();
        private final int killAfterCheckpointSaves;
        private final AtomicInteger checkpointSaves = new AtomicInteger();
        private final AtomicReference<String> lastCheckpointRunId = new AtomicReference<>();

        private KillingSuspendedRunRepository(int killAfterCheckpointSaves) {
            this.killAfterCheckpointSaves = killAfterCheckpointSaves;
        }

        @Override
        public void save(SuspendedRun run) {
            delegate.save(run);
        }

        @Override
        public void saveCheckpoint(SuspendedRun checkpoint) {
            delegate.saveCheckpoint(checkpoint);
            lastCheckpointRunId.set(checkpoint.runId());
            if (checkpointSaves.incrementAndGet() == killAfterCheckpointSaves) {
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

        String lastCheckpointRunId() {
            return lastCheckpointRunId.get();
        }
    }

    private static final class RecordingSuspendedRunRepository implements SuspendedRunRepository {
        private final InMemorySuspendedRunRepository delegate = new InMemorySuspendedRunRepository();
        private final AtomicReference<String> runId = new AtomicReference<>();

        @Override
        public void save(SuspendedRun run) {
            delegate.save(run);
            runId.set(run.runId());
        }

        @Override
        public void saveCheckpoint(SuspendedRun checkpoint) {
            delegate.saveCheckpoint(checkpoint);
            runId.set(checkpoint.runId());
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

        String awaitRunId() throws InterruptedException {
            for (int i = 0; i < 100; i++) {
                String value = runId.get();
                if (value != null) {
                    return value;
                }
                Thread.sleep(10);
            }
            throw new AssertionError("runId was not recorded");
        }

        void awaitHeartbeatAfter(String runId, Instant heartbeat) throws InterruptedException {
            for (int i = 0; i < 100; i++) {
                Optional<SuspendedRun> current = findByRunId(runId);
                if (current.isPresent() && current.get().heartbeatAt().isAfter(heartbeat)) {
                    return;
                }
                Thread.sleep(10);
            }
            throw new AssertionError("heartbeat was not refreshed");
        }
    }

    private static final class ProcessKilled extends Error {
        private static final long serialVersionUID = 1L;
    }
}
