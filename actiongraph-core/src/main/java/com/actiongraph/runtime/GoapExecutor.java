package com.actiongraph.runtime;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionExecutionPolicy;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.api.Experimental;
import com.actiongraph.durability.RecoveryPolicy;
import com.actiongraph.events.EventPayload;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.exception.ActionGraphIntegrationException;
import com.actiongraph.observability.NoopObservationSink;
import com.actiongraph.observability.ObservationEvent;
import com.actiongraph.observability.ObservationEventType;
import com.actiongraph.observability.ObservationSink;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.planning.Plan;
import com.actiongraph.planning.Planner;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.DataMaskingPolicy;
import com.actiongraph.policy.ExecutionPolicyGuard;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.HumanReviewRequest;
import com.actiongraph.policy.HumanReviewResult;
import com.actiongraph.policy.NoopMaskingPolicy;
import com.actiongraph.policy.NoopReviewAttributeContributor;
import com.actiongraph.policy.PendingHumanReviewPolicy;
import com.actiongraph.policy.PolicyDecision;
import com.actiongraph.policy.ReviewAttributeContributor;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceHasher;
import com.actiongraph.trace.TraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public final class GoapExecutor implements Executor {
    public static final int DEFAULT_MAX_STEPS = 64;
    public static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    public static final Duration DEFAULT_EVENT_WAIT_TIMEOUT = Duration.ofHours(24);
    private static final Logger LOGGER = LoggerFactory.getLogger(GoapExecutor.class);

    private final Planner planner;
    private final ExecutionPolicyGuard policyGuard;
    private final HumanReviewPolicy humanReviewPolicy;
    private final TraceRepository traceRepository;
    private final SuspendedRunRepository suspendedRunRepository;
    private final DataMaskingPolicy maskingPolicy;
    private final ReviewAttributeContributor reviewAttributeContributor;
    private final ObservationSink observationSink;
    private final int maxSteps;
    private final boolean durabilityEnabled;
    private final Duration heartbeatInterval;
    private final Duration defaultEventWaitTimeout;

    public GoapExecutor() {
        this(new GoapPlanner(), new DefaultPolicyGuard(), new PendingHumanReviewPolicy(),
                new InMemoryTraceRepository(), new InMemorySuspendedRunRepository(), DEFAULT_MAX_STEPS);
    }

    public GoapExecutor(Planner planner, ExecutionPolicyGuard policyGuard, TraceRepository traceRepository) {
        this(planner, policyGuard, new PendingHumanReviewPolicy(), traceRepository,
                new InMemorySuspendedRunRepository(), DEFAULT_MAX_STEPS);
    }

    public GoapExecutor(
            Planner planner,
            ExecutionPolicyGuard policyGuard,
            HumanReviewPolicy humanReviewPolicy,
            TraceRepository traceRepository
    ) {
        this(planner, policyGuard, humanReviewPolicy, traceRepository,
                new InMemorySuspendedRunRepository(), DEFAULT_MAX_STEPS);
    }

    public GoapExecutor(
            Planner planner,
            ExecutionPolicyGuard policyGuard,
            HumanReviewPolicy humanReviewPolicy,
            TraceRepository traceRepository,
            SuspendedRunRepository suspendedRunRepository
    ) {
        this(planner, policyGuard, humanReviewPolicy, traceRepository, suspendedRunRepository, DEFAULT_MAX_STEPS);
    }

    public GoapExecutor(
            Planner planner,
            ExecutionPolicyGuard policyGuard,
            TraceRepository traceRepository,
            int maxSteps
    ) {
        this(planner, policyGuard, new PendingHumanReviewPolicy(), traceRepository,
                new InMemorySuspendedRunRepository(), maxSteps);
    }

    public GoapExecutor(
            Planner planner,
            ExecutionPolicyGuard policyGuard,
            HumanReviewPolicy humanReviewPolicy,
            TraceRepository traceRepository,
            SuspendedRunRepository suspendedRunRepository,
            int maxSteps
    ) {
        this(planner, policyGuard, humanReviewPolicy, traceRepository, suspendedRunRepository,
                NoopMaskingPolicy.INSTANCE, NoopReviewAttributeContributor.INSTANCE,
                NoopObservationSink.INSTANCE, maxSteps, false,
                DEFAULT_HEARTBEAT_INTERVAL, DEFAULT_EVENT_WAIT_TIMEOUT);
    }

    private GoapExecutor(
            Planner planner,
            ExecutionPolicyGuard policyGuard,
            HumanReviewPolicy humanReviewPolicy,
            TraceRepository traceRepository,
            SuspendedRunRepository suspendedRunRepository,
            DataMaskingPolicy maskingPolicy,
            ReviewAttributeContributor reviewAttributeContributor,
            ObservationSink observationSink,
            int maxSteps,
            boolean durabilityEnabled,
            Duration heartbeatInterval,
            Duration defaultEventWaitTimeout
    ) {
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps must be > 0");
        }
        Duration validatedHeartbeatInterval = Objects.requireNonNull(heartbeatInterval, "heartbeatInterval");
        if (validatedHeartbeatInterval.isZero() || validatedHeartbeatInterval.isNegative()) {
            throw new IllegalArgumentException("heartbeatInterval must be positive");
        }
        Duration validatedEventWaitTimeout = Objects.requireNonNull(defaultEventWaitTimeout, "defaultEventWaitTimeout");
        if (validatedEventWaitTimeout.isZero() || validatedEventWaitTimeout.isNegative()) {
            throw new IllegalArgumentException("defaultEventWaitTimeout must be positive");
        }
        this.planner = Objects.requireNonNull(planner, "planner");
        this.policyGuard = Objects.requireNonNull(policyGuard, "policyGuard");
        this.humanReviewPolicy = Objects.requireNonNull(humanReviewPolicy, "humanReviewPolicy");
        this.traceRepository = Objects.requireNonNull(traceRepository, "traceRepository");
        this.suspendedRunRepository = Objects.requireNonNull(suspendedRunRepository, "suspendedRunRepository");
        this.maskingPolicy = Objects.requireNonNull(maskingPolicy, "maskingPolicy");
        this.reviewAttributeContributor = Objects.requireNonNull(reviewAttributeContributor, "reviewAttributeContributor");
        this.observationSink = Objects.requireNonNull(observationSink, "observationSink");
        this.maxSteps = maxSteps;
        this.durabilityEnabled = durabilityEnabled;
        this.heartbeatInterval = validatedHeartbeatInterval;
        this.defaultEventWaitTimeout = validatedEventWaitTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public TraceRepository traceRepository() {
        return traceRepository;
    }

    public SuspendedRunRepository suspendedRunRepository() {
        return suspendedRunRepository;
    }

    @Override
    public RunResult run(Goal goal, Blackboard initial, Collection<Action> actions, ActionRegistry registry) {
        return run(goal, initial, actions, registry, Map.of());
    }

    public RunResult run(
            Goal goal,
            Blackboard initial,
            Collection<Action> actions,
            ActionRegistry registry,
            Map<String, String> runMetadata
    ) {
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(registry, "registry");

        Map<String, String> normalizedRunMetadata = normalizeMetadata(runMetadata);
        String runId = UUID.randomUUID().toString();
        long runStartedAt = System.nanoTime();
        LOGGER.debug("Starting run: runId={}, goal='{}', candidateActions={}, metadataKeys={}",
                runId, goal.name(), actions.size(), normalizedRunMetadata.keySet());
        observe(ObservationEvent.of(runId, ObservationEventType.RUN_STARTED, null, Map.of(
                "goal", goal.name(),
                "candidateActions", Integer.toString(actions.size())
        )));
        RunTrace trace = new RunTrace(traceRepository, runId, maskingPolicy, observationSink);
        trace.append(TraceEventType.RUN_STARTED, null, "Run started", traceData(normalizedRunMetadata, Map.of(
                "goal", goal.name(),
                "targetConditions", conditionKeys(goal.targetConditions())
        )));
        return runLoop(
                runId,
                goal,
                initial,
                actions,
                registry,
                new ArrayList<>(),
                new ArrayDeque<>(),
                trace,
                normalizedRunMetadata,
                runStartedAt,
                true
        );
    }

    public RunResult resume(String runId, Collection<Action> actions, ActionRegistry registry) {
        return resume(runId, actions, registry, Map.of());
    }

    public RunResult resume(
            String runId,
            Collection<Action> actions,
            ActionRegistry registry,
            Map<String, String> runMetadata
    ) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(registry, "registry");

        Map<String, String> normalizedRunMetadata = normalizeMetadata(runMetadata);
        long runStartedAt = System.nanoTime();
        LOGGER.debug("Attempting resume: runId={}, candidateActions={}, metadataKeys={}",
                runId, actions.size(), normalizedRunMetadata.keySet());
        SuspendedRun suspendedRun = suspendedRunRepository.claimForResume(runId)
                .orElseThrow(() -> new SuspendedRunNotClaimableException(runId));
        LOGGER.debug("Resume claim succeeded: runId={}, pendingActionId={}, executedActions={}, compensationStack={}",
                runId,
                suspendedRun.pendingActionId().value(),
                suspendedRun.executedActions().size(),
                suspendedRun.compensationStack().size());
        observe(ObservationEvent.of(runId, ObservationEventType.RUN_RESUMED, suspendedRun.pendingActionId(), Map.of(
                "candidateActions", Integer.toString(actions.size()),
                "executedActions", Integer.toString(suspendedRun.executedActions().size()),
                "compensationStack", Integer.toString(suspendedRun.compensationStack().size())
        )));
        RunTrace trace = new RunTrace(traceRepository, runId, maskingPolicy, observationSink);
        trace.append(TraceEventType.RUN_RESUMED, suspendedRun.pendingActionId(), "Run resumed", traceData(normalizedRunMetadata, Map.of(
                "pendingActionId", suspendedRun.pendingActionId().value()
        )));
        return runLoop(
                runId,
                suspendedRun.goal(),
                suspendedRun.blackboard(),
                actions,
                registry,
                new ArrayList<>(suspendedRun.executedActions()),
                rehydrateCompensationStack(suspendedRun, registry),
                trace,
                normalizedRunMetadata,
                runStartedAt,
                true
        );
    }

    @Experimental(
            since = "0.2.0",
            value = "Crash recovery entry point is experimental until MS1 recovery pilots complete."
    )
    public RunResult recover(
            SuspendedRun checkpoint,
            Collection<Action> actions,
            ActionRegistry registry,
            RecoveryPolicy recoveryPolicy
    ) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(recoveryPolicy, "recoveryPolicy");
        if (checkpoint.snapshotState() != SnapshotState.RUNNING) {
            throw new IllegalArgumentException("Only RUNNING checkpoints can be recovered");
        }

        long runStartedAt = System.nanoTime();
        String runId = checkpoint.runId();
        RunTrace trace = new RunTrace(traceRepository, runId, maskingPolicy, observationSink);
        trace.append(TraceEventType.RUN_RECOVERED, checkpoint.inFlightActionId(), "Run recovered", Map.of(
                "policy", recoveryPolicy.name(),
                "inFlightActionId", checkpoint.inFlightActionId() == null
                        ? "" : checkpoint.inFlightActionId().value(),
                "executedActions", Integer.toString(checkpoint.executedActions().size()),
                "compensationStack", Integer.toString(checkpoint.compensationStack().size())
        ));
        ExecutionContext context = new DefaultExecutionContext(checkpoint.blackboard(), traceRepository, runId);
        Deque<Action> compensationStack = rehydrateCompensationStack(checkpoint, registry);
        if (checkpoint.inFlightActionId() != null) {
            Action inFlight = registry.byId(checkpoint.inFlightActionId())
                    .orElseThrow(() -> new ActionGraphConfigurationException(
                            "In-flight action is not registered: " + checkpoint.inFlightActionId().value()));
            boolean compensated = compensateOne(inFlight, context, trace);
            if (!compensated) {
                return finish(trace, runId, RunStatus.FAILED_COMPENSATION_INCOMPLETE,
                        checkpoint.blackboard(), checkpoint.executedActions(),
                        "Recovered in-flight action could not be compensated", runStartedAt);
            }
        }
        if (recoveryPolicy == RecoveryPolicy.COMPENSATE) {
            boolean compensated = compensateAll(compensationStack, context, trace);
            RunStatus status = compensated
                    ? RunStatus.FAILED_COMPENSATED
                    : RunStatus.FAILED_COMPENSATION_INCOMPLETE;
            return finish(trace, runId, status, checkpoint.blackboard(), checkpoint.executedActions(),
                    "Recovered run compensated", runStartedAt);
        }
        return runLoop(
                runId,
                checkpoint.goal(),
                checkpoint.blackboard(),
                actions,
                registry,
                new ArrayList<>(checkpoint.executedActions()),
                compensationStack,
                trace,
                Map.of(),
                runStartedAt,
                true
        );
    }

    @Experimental(
            since = "0.2.0",
            value = "External event resume entry point is experimental until MS2 event ingress pilots complete."
    )
    public RunResult resumeFromWaitingEvent(
            SuspendedRun checkpoint,
            Collection<Action> actions,
            ActionRegistry registry,
            String eventType,
            String correlationId,
            EventPayload payload
    ) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(payload, "payload");
        if (checkpoint.snapshotState() != SnapshotState.RUNNING) {
            throw new IllegalArgumentException("Event resume requires a RUNNING checkpoint");
        }

        long runStartedAt = System.nanoTime();
        RunTrace trace = new RunTrace(traceRepository, checkpoint.runId(), maskingPolicy, observationSink);
        return runLoop(
                checkpoint.runId(),
                checkpoint.goal(),
                checkpoint.blackboard(),
                actions,
                registry,
                new ArrayList<>(checkpoint.executedActions()),
                rehydrateCompensationStack(checkpoint, registry),
                trace,
                Map.of(),
                runStartedAt,
                true
        );
    }

    @Experimental(
            since = "0.2.0",
            value = "External event trace checkpointing is experimental until MS2 event ingress pilots complete."
    )
    public void recordEventDelivered(
            String runId,
            String eventType,
            String correlationId,
            EventPayload payload
    ) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(payload, "payload");
        RunTrace trace = new RunTrace(traceRepository, runId, maskingPolicy, observationSink);
        trace.append(TraceEventType.EVENT_DELIVERED, null, "External event delivered", Map.of(
                "eventType", eventType,
                "correlationId", correlationId,
                "contentType", payload.contentType()
        ));
        trace.flush();
    }

    @Experimental(
            since = "0.2.0",
            value = "External event timeout handling is experimental until MS2 event ingress pilots complete."
    )
    public RunResult timeoutWaitingEvent(
            SuspendedRun waitingRun,
            Collection<Action> actions,
            ActionRegistry registry
    ) {
        Objects.requireNonNull(waitingRun, "waitingRun");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(registry, "registry");
        if (waitingRun.snapshotState() != SnapshotState.WAITING_EVENT) {
            throw new IllegalArgumentException("Only WAITING_EVENT snapshots can time out");
        }

        long runStartedAt = System.nanoTime();
        RunTrace trace = new RunTrace(traceRepository, waitingRun.runId(), maskingPolicy, observationSink);
        trace.append(TraceEventType.EVENT_WAIT_TIMED_OUT, null, "External event wait timed out", Map.of(
                "eventType", waitingRun.eventType() == null ? "" : waitingRun.eventType(),
                "correlationId", waitingRun.eventCorrelationId() == null ? "" : waitingRun.eventCorrelationId(),
                "deadline", waitingRun.eventDeadline() == null ? "" : waitingRun.eventDeadline().toString()
        ));
        ExecutionContext context = new DefaultExecutionContext(waitingRun.blackboard(), traceRepository, waitingRun.runId());
        boolean compensated = compensateAll(rehydrateCompensationStack(waitingRun, registry), context, trace);
        RunStatus status = compensated
                ? RunStatus.FAILED_COMPENSATED
                : RunStatus.FAILED_COMPENSATION_INCOMPLETE;
        return finish(trace, waitingRun.runId(), status, waitingRun.blackboard(), waitingRun.executedActions(),
                "External event wait timed out", runStartedAt);
    }

    private static Map<String, String> normalizeMetadata(Map<String, String> metadata) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    throw new IllegalArgumentException("run metadata key must not be blank");
                }
                merged.put(key, value == null ? "" : value);
            });
        }
        return Map.copyOf(merged);
    }

    private static Map<String, String> traceData(Map<String, String> metadata, Map<String, String> data) {
        Map<String, String> merged = new LinkedHashMap<>(metadata);
        merged.putAll(data);
        return merged;
    }

    private RunResult runLoop(
            String runId,
            Goal goal,
            Blackboard blackboard,
            Collection<Action> actions,
            ActionRegistry registry,
            List<ActionId> executedActionIds,
            Deque<Action> compensationStack,
            RunTrace trace,
            Map<String, String> runMetadata,
            long runStartedAt,
            boolean checkpointAtStart
    ) {
        ExecutionContext context = new DefaultExecutionContext(blackboard, traceRepository, runId);
        List<Action> allActions = List.copyOf(actions);

        try (Heartbeat heartbeat = startHeartbeat(runId)) {
            if (checkpointAtStart) {
                saveInitialCheckpoint(runId, goal, blackboard, executedActionIds, compensationStack);
            }
            for (int step = 0; step < maxSteps; step++) {
            Set<Condition> state = blackboard.conditions();
            LOGGER.debug("Run step started: runId={}, step={}, conditions={}",
                    runId, step + 1, state.size());
            if (goal.isSatisfiedBy(state)) {
                trace.append(TraceEventType.GOAL_SATISFIED, null, "Goal satisfied", Map.of(
                        "conditions", conditionKeys(state)
                ));
                LOGGER.debug("Goal satisfied: runId={}, goal='{}', executedActions={}",
                        runId, goal.name(), executedActionIds.size());
                return finish(trace, runId, RunStatus.COMPLETED, blackboard, executedActionIds,
                        "Goal satisfied", runStartedAt);
            }

            Selection selection = selectNextAction(
                    runId, goal, state, blackboard, allActions, registry, trace, runMetadata);
            if (selection.halted()) {
                LOGGER.debug("Run selection halted: runId={}, status={}, pendingActionId={}, compensateOnHalt={}",
                        runId,
                        selection.status(),
                        selection.pendingActionId() == null ? "" : selection.pendingActionId().value(),
                        selection.compensateOnHalt());
                if (selection.status() == RunStatus.SUSPENDED_PENDING_REVIEW) {
                    trace.flush();
                    saveSuspendedRun(runId, goal, blackboard, executedActionIds,
                            compensationStack, selection.pendingActionId(), selection.message());
                    return finish(trace, runId, selection.status(), blackboard, executedActionIds,
                            selection.message(), runStartedAt);
                }
                if (selection.compensateOnHalt()) {
                    boolean compensated = compensateAll(compensationStack, context, trace);
                    if (!compensated) {
                        return finish(trace, runId, RunStatus.FAILED_COMPENSATION_INCOMPLETE,
                                blackboard, executedActionIds, selection.message(), runStartedAt);
                    }
                }
                return finish(trace, runId, selection.status(), blackboard, executedActionIds,
                        selection.message(), runStartedAt);
            }

            Action action = selection.action();
            Map<BlackboardKey<?>, Object> beforeObjects = blackboard.snapshotEntries();
            LOGGER.debug("Executing action: runId={}, actionId={}, riskLevel={}",
                    runId, action.id().value(), action.riskLevel());
            trace.append(TraceEventType.ACTION_STARTED, action.id(), "Action started", Map.of(
                    "riskLevel", action.riskLevel().name()
            ));
            observe(ObservationEvent.of(runId, ObservationEventType.ACTION_STARTED, action.id(), Map.of(
                    "riskLevel", action.riskLevel().name()
            )));

            long actionStartedAt = System.nanoTime();
            ActionExecutionOutcome outcome = executeWithPolicy(action, context, trace);
            observe(ObservationEvent.timed(runId, ObservationEventType.ACTION_FINISHED, action.id(),
                    outcome.observationTags(), elapsedSince(actionStartedAt)));

            ActionResult result = outcome.result();
            if (result.success()) {
                Set<Condition> addedConditions = new LinkedHashSet<>(action.effects());
                addedConditions.addAll(result.producedConditions());
                addedConditions.forEach(blackboard::addCondition);
                executedActionIds.add(action.id());
                compensationStack.push(action);

                trace.append(TraceEventType.ACTION_SUCCEEDED, action.id(), result.message(), Map.of(
                        "conditions", conditionKeys(addedConditions)
                ));
                LOGGER.debug("Action succeeded: runId={}, actionId={}, addedConditions={}",
                        runId, action.id().value(), addedConditions.size());
                traceBlackboardUpdate(trace, action, beforeObjects, blackboard.snapshotEntries(), addedConditions);
                if (result.waiting()) {
                    Instant deadline = Instant.now().plus(result.eventTimeout() == null
                            ? defaultEventWaitTimeout
                            : result.eventTimeout());
                    String eventType = Objects.requireNonNull(result.eventType(), "eventType");
                    String correlationId = Objects.requireNonNull(result.eventCorrelationId(), "eventCorrelationId");
                    trace.append(TraceEventType.EVENT_WAIT_STARTED, action.id(), result.message(), Map.of(
                            "eventType", eventType,
                            "correlationId", correlationId,
                            "deadline", deadline.toString()
                    ));
                    trace.flush();
                    saveWaitingEventRun(runId, goal, blackboard, executedActionIds,
                            compensationStack, result.message(), eventType, correlationId, deadline);
                    return finish(trace, runId, RunStatus.SUSPENDED_WAITING_EVENT, blackboard, executedActionIds,
                            result.message(), runStartedAt);
                }
                checkpointAfterAction(runId, goal, blackboard, executedActionIds, compensationStack, trace, action.id());
            } else {
                if (outcome.timedOut()) {
                    executedActionIds.add(action.id());
                    compensationStack.push(action);
                    trace.append(TraceEventType.ACTION_TIMED_OUT, action.id(), result.message(), Map.of(
                            "outcome", "UNKNOWN",
                            "attempt", Integer.toString(outcome.attempts())
                    ));
                    LOGGER.debug("Action timed out with unknown outcome: runId={}, actionId={}, attempt={}",
                            runId, action.id().value(), outcome.attempts());
                }
                trace.append(TraceEventType.ACTION_FAILED, action.id(), result.message(), Map.of());
                LOGGER.debug("Action failed, starting compensation: runId={}, actionId={}, compensationStack={}",
                        runId, action.id().value(), compensationStack.size());
                boolean compensated = compensateAll(compensationStack, context, trace);
                RunStatus status = compensated
                        ? RunStatus.FAILED_COMPENSATED
                        : RunStatus.FAILED_COMPENSATION_INCOMPLETE;
                return finish(trace, runId, status, blackboard, executedActionIds, result.message(), runStartedAt);
            }
        }

        trace.append(TraceEventType.NO_PLAN, null, "Max executor steps exceeded", Map.of(
                "maxSteps", Integer.toString(maxSteps)
        ));
        LOGGER.debug("Max executor steps exceeded: runId={}, maxSteps={}", runId, maxSteps);
        return finish(trace, runId, RunStatus.HALTED_UNREACHABLE, blackboard, executedActionIds,
                "Max executor steps exceeded", runStartedAt);
        }
    }

    private ActionExecutionOutcome executeWithPolicy(Action action, ExecutionContext context, RunTrace trace) {
        ActionExecutionPolicy policy = action.executionPolicy();
        Map<String, String> tags = new LinkedHashMap<>();
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            markInFlight(context.runId(), action.id());
            ExecutionContext attemptContext = new DefaultExecutionContext(
                    context.blackboard(), context.trace(), context.runId(), attempt);
            AttemptResult attemptResult = executeAttempt(action, attemptContext, policy.timeout());
            if (attemptResult.timedOut()) {
                tags.put("success", "false");
                tags.put("timedOut", "true");
                tags.put("attempts", Integer.toString(attempt));
                return new ActionExecutionOutcome(
                        ActionResult.fail("Action timed out with unknown outcome: " + action.id().value()),
                        true,
                        attempt,
                        tags
                );
            }
            if (attemptResult.exception() != null) {
                RuntimeException ex = attemptResult.exception();
                LOGGER.debug("Action raised exception: runId={}, actionId={}, attempt={}",
                        context.runId(), action.id().value(), attempt, ex);
                tags.put("success", "false");
                tags.put("exceptionType", ex.getClass().getName());
                if (attempt < policy.maxAttempts()) {
                    traceRetry(action, trace, attempt, policy.backoff(), "exception");
                    backoff(policy.backoff());
                    continue;
                }
                tags.put("attempts", Integer.toString(attempt));
                return new ActionExecutionOutcome(
                        ActionResult.fail("Exception executing " + action.id().value() + ": " + ex.getMessage()),
                        false,
                        attempt,
                        tags
                );
            }

            ActionResult result = attemptResult.result();
            if (result.success() || attempt == policy.maxAttempts()) {
                tags.put("success", Boolean.toString(result.success()));
                tags.put("attempts", Integer.toString(attempt));
                return new ActionExecutionOutcome(result, false, attempt, tags);
            }
            traceRetry(action, trace, attempt, policy.backoff(), "failed-result");
            backoff(policy.backoff());
        }
        tags.put("success", "false");
        tags.put("attempts", Integer.toString(policy.maxAttempts()));
        return new ActionExecutionOutcome(
                ActionResult.fail("Action failed after " + policy.maxAttempts() + " attempts: " + action.id().value()),
                false,
                policy.maxAttempts(),
                tags
        );
    }

    private AttemptResult executeAttempt(Action action, ExecutionContext context, Duration timeout) {
        if (timeout == null) {
            return callAction(action, context);
        }
        FutureTask<ActionResult> task = new FutureTask<>(() -> action.execute(context));
        Thread thread = Thread.ofVirtual().name("actiongraph-action-" + action.id().value()).start(task);
        try {
            return AttemptResult.result(task.get(timeout.toNanos(), TimeUnit.NANOSECONDS));
        } catch (TimeoutException ex) {
            task.cancel(true);
            thread.interrupt();
            return AttemptResult.timeout();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return AttemptResult.exception(new RuntimeException("Interrupted while executing action", ex));
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                return AttemptResult.exception(runtimeException);
            }
            if (cause instanceof Error error) {
                throw error;
            }
            return AttemptResult.exception(new RuntimeException(cause));
        }
    }

    private AttemptResult callAction(Action action, ExecutionContext context) {
        try {
            return AttemptResult.result(action.execute(context));
        } catch (RuntimeException ex) {
            return AttemptResult.exception(ex);
        }
    }

    private void traceRetry(Action action, RunTrace trace, int attempt, Duration backoff, String reason) {
        trace.append(TraceEventType.ACTION_RETRIED, action.id(), "Retrying action " + action.id().value(), Map.of(
                "attempt", Integer.toString(attempt),
                "nextAttempt", Integer.toString(attempt + 1),
                "backoffMillis", Long.toString(backoff.toMillis()),
                "reason", reason
        ));
    }

    private void backoff(Duration backoff) {
        if (backoff.isZero()) {
            return;
        }
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Selection selectNextAction(
            String runId,
            Goal goal,
            Set<Condition> state,
            Blackboard blackboard,
            List<Action> allActions,
            ActionRegistry registry,
            RunTrace trace,
            Map<String, String> runMetadata
    ) {
        Set<ActionId> excluded = new LinkedHashSet<>();
        ActionId guardBlockedAction = null;

        while (true) {
            List<Action> candidateActions = allActions.stream()
                    .filter(action -> !excluded.contains(action.id()))
                    .toList();
            Optional<Plan> planOpt = planner.plan(goal, state, candidateActions);
            if (planOpt.isEmpty() || planOpt.get().isEmpty()) {
                String detail = guardBlockedAction == null
                        ? "No plan from current state"
                        : "No alternative plan after runtime guard failed for " + guardBlockedAction.value();
                trace.append(TraceEventType.NO_PLAN, guardBlockedAction, detail, Map.of(
                        "excludedActions", actionIdKeys(excluded),
                        "state", conditionKeys(state)
                ));
                observe(ObservationEvent.of(runId, ObservationEventType.NO_PLAN, guardBlockedAction, Map.of(
                        "excludedActions", Integer.toString(excluded.size()),
                        "reason", guardBlockedAction == null ? "no-plan" : "runtime-guard"
                )));
                LOGGER.debug("No executable plan: runId={}, goal='{}', excludedActions={}, detail={}",
                        runId, goal.name(), excluded.size(), detail);
                return Selection.halt(RunStatus.HALTED_UNREACHABLE, false, guardBlockedAction, detail);
            }

            Plan plan = planOpt.get();
            LOGGER.debug("Plan generated: runId={}, steps={}", runId, plan.steps().size());
            trace.append(TraceEventType.PLAN_GENERATED, null, "Plan generated", Map.of(
                    "steps", plan.steps().stream()
                            .map(step -> step.actionId().value())
                            .collect(Collectors.joining(","))
            ));
            observe(ObservationEvent.of(runId, ObservationEventType.PLAN_GENERATED, null, Map.of(
                    "steps", Integer.toString(plan.steps().size())
            )));

            ActionId actionId = plan.steps().getFirst().actionId();
            Optional<Action> actionOpt = registry.byId(actionId);
            if (actionOpt.isEmpty()) {
                String detail = "Planned action is not registered: " + actionId.value();
                trace.append(TraceEventType.NO_PLAN, actionId, detail, Map.of());
                observe(ObservationEvent.of(runId, ObservationEventType.NO_PLAN, actionId, Map.of(
                        "reason", "unregistered-action"
                )));
                LOGGER.debug("Planned action missing from registry: runId={}, actionId={}",
                        runId, actionId.value());
                return Selection.halt(RunStatus.HALTED_UNREACHABLE, false, actionId, detail);
            }

            Action action = actionOpt.get();
            PolicyDecision decision = policyGuard.evaluate(action, blackboard);
            LOGGER.debug("Policy evaluated: runId={}, actionId={}, decision={}",
                    runId, action.id().value(), decision);
            trace.append(TraceEventType.POLICY_EVALUATED, action.id(), "Policy evaluated", Map.of(
                    "decision", decision.name()
            ));
            observe(ObservationEvent.of(runId, ObservationEventType.POLICY_EVALUATED, action.id(), Map.of(
                    "decision", decision.name()
            )));
            if (decision == PolicyDecision.DENY) {
                String detail = "Policy denied action: " + action.id().value();
                trace.append(TraceEventType.POLICY_DENIED, action.id(), detail, Map.of());
                LOGGER.debug("Policy denied action: runId={}, actionId={}", runId, action.id().value());
                return Selection.halt(RunStatus.DENIED_BY_POLICY, true, action.id(), detail);
            }
            if (decision == PolicyDecision.REQUIRES_HUMAN_REVIEW) {
                LOGGER.debug("Human review requested: runId={}, actionId={}, riskLevel={}",
                        runId, action.id().value(), action.riskLevel());
                trace.append(TraceEventType.HUMAN_REVIEW_REQUESTED, action.id(),
                        "Human review requested", Map.of(
                                "riskLevel", action.riskLevel().name(),
                                "planPreview", plan.steps().stream()
                                        .map(step -> step.actionId().value())
                                        .collect(Collectors.joining(","))
                        ));
                observe(ObservationEvent.of(runId, ObservationEventType.HUMAN_REVIEW_REQUESTED, action.id(), Map.of(
                        "riskLevel", action.riskLevel().name(),
                        "requiredByAction", Boolean.toString(action.requiresHumanReview())
                )));
                trace.flush();
                Map<String, String> attributes = reviewAttributes(action, blackboard, runMetadata);
                HumanReviewResult review = humanReviewPolicy.review(new HumanReviewRequest(
                        runId,
                        action.id(),
                        action.riskLevel(),
                        action.requiresHumanReview(),
                        plan,
                        state,
                        maskingPolicy.maskData(objectPreview(blackboard.snapshotEntries())),
                        attributes
                ));
                trace.append(TraceEventType.HUMAN_REVIEW_DECIDED, action.id(),
                        review.message(), Map.of(
                                "decision", review.decision().name(),
                                "reviewer", review.reviewer()
                        ));
                observe(ObservationEvent.of(runId, ObservationEventType.HUMAN_REVIEW_DECIDED, action.id(), Map.of(
                        "decision", review.decision().name()
                )));
                LOGGER.debug("Human review decided: runId={}, actionId={}, decision={}",
                        runId, action.id().value(), review.decision());
                if (review.decision() == HumanReviewDecision.PENDING) {
                    return Selection.halt(RunStatus.SUSPENDED_PENDING_REVIEW, false, action.id(), review.message());
                }
                if (review.decision() == HumanReviewDecision.DENIED) {
                    trace.append(TraceEventType.POLICY_DENIED, action.id(), review.message(), Map.of(
                            "reviewer", review.reviewer()
                    ));
                    return Selection.halt(RunStatus.DENIED_BY_POLICY, true, action.id(), review.message());
                }
            }

            if (!action.runtimeGuard(blackboard)) {
                guardBlockedAction = action.id();
                excluded.add(action.id());
                trace.append(TraceEventType.RUNTIME_GUARD_FAILED, action.id(), "Runtime guard failed", Map.of(
                        "state", conditionKeys(state)
                ));
                observe(ObservationEvent.of(runId, ObservationEventType.RUNTIME_GUARD_FAILED, action.id(), Map.of()));
                LOGGER.debug("Runtime guard failed, excluding action and replanning: runId={}, actionId={}, excludedActions={}",
                        runId, action.id().value(), excluded.size());
                continue;
            }

            LOGGER.debug("Action selected: runId={}, actionId={}", runId, action.id().value());
            return Selection.action(action);
        }
    }

    private Map<String, String> reviewAttributes(
            Action action,
            Blackboard blackboard,
            Map<String, String> runMetadata
    ) {
        Map<String, String> attributes = new LinkedHashMap<>(runMetadata);
        Map<String, String> contributed = reviewAttributeContributor.contribute(action, blackboard);
        if (contributed != null) {
            contributed.forEach((key, value) -> attributes.put(key, value == null ? "" : value));
        }
        return maskingPolicy.maskData(attributes);
    }

    private boolean compensateAll(Deque<Action> compensationStack, ExecutionContext context, RunTrace trace) {
        trace.flush();
        boolean allOk = true;
        LOGGER.debug("Compensation started: runId={}, compensationStack={}", context.runId(),
                compensationStack.size());
        observe(ObservationEvent.of(context.runId(), ObservationEventType.COMPENSATION_STARTED, null, Map.of(
                "compensationStack", Integer.toString(compensationStack.size())
        )));
        while (!compensationStack.isEmpty()) {
            Action action = compensationStack.pop();
            long compensationStartedAt = System.nanoTime();
            try {
                CompensationResult result = action.compensate(context);
                LOGGER.debug("Compensation completed: runId={}, actionId={}, success={}, noop={}",
                        context.runId(), action.id().value(), result.success(), result.noOp());
                observe(ObservationEvent.timed(context.runId(), ObservationEventType.COMPENSATION_FINISHED,
                        action.id(), Map.of(
                                "success", Boolean.toString(result.success()),
                                "noop", Boolean.toString(result.noOp())
                        ), elapsedSince(compensationStartedAt)));
                trace.append(TraceEventType.COMPENSATED, action.id(), result.message(), Map.of(
                        "success", Boolean.toString(result.success()),
                        "noop", Boolean.toString(result.noOp())
                ));
                if (!result.success() && !result.noOp()) {
                    allOk = false;
                }
                trace.flush();
            } catch (Exception ex) {
                allOk = false;
                LOGGER.debug("Compensation raised exception: runId={}, actionId={}",
                        context.runId(), action.id().value(), ex);
                observe(ObservationEvent.timed(context.runId(), ObservationEventType.COMPENSATION_FINISHED,
                        action.id(), Map.of(
                                "success", "false",
                                "noop", "false",
                                "exceptionType", ex.getClass().getName()
                        ), elapsedSince(compensationStartedAt)));
                trace.append(TraceEventType.COMPENSATION_ERROR, action.id(), ex.getMessage(), Map.of(
                        "exceptionType", ex.getClass().getName()
                ));
                trace.flush();
            }
        }
        LOGGER.debug("Compensation finished: runId={}, complete={}", context.runId(), allOk);
        return allOk;
    }

    private boolean compensateOne(Action action, ExecutionContext context, RunTrace trace) {
        trace.flush();
        long compensationStartedAt = System.nanoTime();
        try {
            CompensationResult result = action.compensate(context);
            observe(ObservationEvent.timed(context.runId(), ObservationEventType.COMPENSATION_FINISHED,
                    action.id(), Map.of(
                            "success", Boolean.toString(result.success()),
                            "noop", Boolean.toString(result.noOp())
                    ), elapsedSince(compensationStartedAt)));
            trace.append(TraceEventType.COMPENSATED, action.id(), result.message(), Map.of(
                    "success", Boolean.toString(result.success()),
                    "noop", Boolean.toString(result.noOp()),
                    "recoveredInFlight", "true"
            ));
            trace.flush();
            return result.success() || result.noOp();
        } catch (Exception ex) {
            LOGGER.debug("Recovered in-flight compensation raised exception: runId={}, actionId={}",
                    context.runId(), action.id().value(), ex);
            trace.append(TraceEventType.COMPENSATION_ERROR, action.id(), ex.getMessage(), Map.of(
                    "exceptionType", ex.getClass().getName(),
                    "recoveredInFlight", "true"
            ));
            trace.flush();
            return false;
        }
    }

    private void saveSuspendedRun(
            String runId,
            Goal goal,
            Blackboard blackboard,
            List<ActionId> executedActionIds,
            Deque<Action> compensationStack,
            ActionId pendingActionId,
            String message
    ) {
        LOGGER.debug("Saving suspended run: runId={}, pendingActionId={}, executedActions={}, compensationStack={}",
                runId, pendingActionId.value(), executedActionIds.size(), compensationStack.size());
        suspendedRunRepository.save(new SuspendedRun(
                runId,
                goal,
                blackboard,
                executedActionIds,
                compensationStack.stream().map(Action::id).toList(),
                pendingActionId,
                message,
                SnapshotState.SUSPENDED,
                Instant.now(),
                null
        ));
    }

    private void saveWaitingEventRun(
            String runId,
            Goal goal,
            Blackboard blackboard,
            List<ActionId> executedActionIds,
            Deque<Action> compensationStack,
            String message,
            String eventType,
            String correlationId,
            Instant deadline
    ) {
        LOGGER.debug("Saving event wait: runId={}, eventType={}, correlationId={}, deadline={}",
                runId, eventType, correlationId, deadline);
        suspendedRunRepository.save(SuspendedRun.waitingForEvent(
                runId,
                goal,
                copyBlackboard(blackboard),
                executedActionIds,
                compensationStack.stream().map(Action::id).toList(),
                message,
                eventType,
                correlationId,
                deadline
        ));
    }

    private void saveInitialCheckpoint(
            String runId,
            Goal goal,
            Blackboard blackboard,
            List<ActionId> executedActionIds,
            Deque<Action> compensationStack
    ) {
        if (!durabilityEnabled) {
            return;
        }
        suspendedRunRepository.saveCheckpoint(new SuspendedRun(
                runId,
                goal,
                copyBlackboard(blackboard),
                executedActionIds,
                compensationStack.stream().map(Action::id).toList(),
                null,
                "Running checkpoint",
                SnapshotState.RUNNING,
                Instant.now(),
                null
        ));
    }

    private void checkpointAfterAction(
            String runId,
            Goal goal,
            Blackboard blackboard,
            List<ActionId> executedActionIds,
            Deque<Action> compensationStack,
            RunTrace trace,
            ActionId actionId
    ) {
        if (!durabilityEnabled) {
            return;
        }
        trace.append(TraceEventType.RUN_CHECKPOINTED, actionId, "Run checkpointed", Map.of(
                "executedActions", Integer.toString(executedActionIds.size()),
                "compensationStack", Integer.toString(compensationStack.size())
        ));
        trace.flush();
        suspendedRunRepository.saveCheckpoint(new SuspendedRun(
                runId,
                goal,
                copyBlackboard(blackboard),
                executedActionIds,
                compensationStack.stream().map(Action::id).toList(),
                null,
                "Running checkpoint",
                SnapshotState.RUNNING,
                Instant.now(),
                null
        ));
    }

    private void markInFlight(String runId, ActionId actionId) {
        if (!durabilityEnabled) {
            return;
        }
        boolean updated = suspendedRunRepository.markInFlight(runId, actionId);
        if (!updated) {
            throw new ActionGraphIntegrationException(
                    "Cannot mark in-flight action without a running checkpoint: " + runId);
        }
    }

    private Deque<Action> rehydrateCompensationStack(SuspendedRun suspendedRun, ActionRegistry registry) {
        Deque<Action> stack = new ArrayDeque<>();
        for (ActionId actionId : suspendedRun.compensationStack()) {
            Action action = registry.byId(actionId)
                    .orElseThrow(() -> new ActionGraphConfigurationException(
                            "Compensation action is not registered: " + actionId.value()));
            stack.addLast(action);
        }
        LOGGER.debug("Rehydrated compensation stack: runId={}, compensationStack={}",
                suspendedRun.runId(), stack.size());
        return stack;
    }

    private void traceBlackboardUpdate(
            RunTrace trace,
            Action action,
            Map<BlackboardKey<?>, Object> beforeObjects,
            Map<BlackboardKey<?>, Object> afterObjects,
            Set<Condition> addedConditions
    ) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("conditions", conditionKeys(addedConditions));
        data.put("objectsAdded", blackboardKeyNames(afterObjects.keySet().stream()
                .filter(key -> !beforeObjects.containsKey(key))
                .collect(Collectors.toCollection(LinkedHashSet::new))));
        data.put("objectsOverwritten", blackboardKeyNames(afterObjects.entrySet().stream()
                .filter(entry -> beforeObjects.containsKey(entry.getKey()))
                .filter(entry -> !Objects.equals(beforeObjects.get(entry.getKey()), entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new))));
        trace.append(TraceEventType.BLACKBOARD_UPDATED, action.id(), "Blackboard updated", data);
    }

    private RunResult finish(
            RunTrace trace,
            String runId,
            RunStatus status,
            Blackboard blackboard,
            List<ActionId> executedActions,
            String message,
            long runStartedAt
    ) {
        if (status == RunStatus.SUSPENDED_PENDING_REVIEW || status == RunStatus.SUSPENDED_WAITING_EVENT) {
            trace.append(TraceEventType.RUN_SUSPENDED, null, message, Map.of("status", status.name()));
        } else {
            suspendedRunRepository.delete(runId);
            trace.append(TraceEventType.RUN_ENDED, null, message, Map.of("status", status.name()));
        }
        trace.flush();
        observe(ObservationEvent.timed(runId, ObservationEventType.RUN_FINISHED, null, Map.of(
                "status", status.name(),
                "executedActions", Integer.toString(executedActions.size()),
                "finalConditions", Integer.toString(blackboard.conditions().size())
        ), elapsedSince(runStartedAt)));
        LOGGER.debug("Run finished: runId={}, status={}, executedActions={}, finalConditions={}",
                runId, status, executedActions.size(), blackboard.conditions().size());
        return new RunResult(runId, status, blackboard.conditions(), executedActions, message);
    }

    private void observe(ObservationEvent event) {
        observe(observationSink, event);
    }

    private static void observe(ObservationSink sink, ObservationEvent event) {
        try {
            sink.observe(event);
        } catch (RuntimeException ex) {
            LOGGER.debug("Observation sink raised exception: runId={}, type={}",
                    event.runId(), event.type(), ex);
        }
    }

    private static long elapsedSince(long startedAt) {
        return Math.max(0, System.nanoTime() - startedAt);
    }

    private String conditionKeys(Collection<Condition> conditions) {
        return conditions.stream()
                .map(Condition::key)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private String actionIdKeys(Collection<ActionId> actionIds) {
        return actionIds.stream()
                .map(ActionId::value)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private String blackboardKeyNames(Collection<BlackboardKey<?>> keys) {
        return keys.stream()
                .map(BlackboardKey::displayName)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private Map<String, String> objectPreview(Map<BlackboardKey<?>, Object> objects) {
        Map<String, String> preview = new LinkedHashMap<>();
        objects.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().type().getName() + "#" + entry.getKey().id()))
                .forEach(entry -> preview.put(
                        entry.getKey().type().getName() + "#" + entry.getKey().id(),
                        String.valueOf(entry.getValue())
                ));
        return preview;
    }

    private Blackboard copyBlackboard(Blackboard source) {
        InMemoryBlackboard copy = new InMemoryBlackboard();
        source.snapshotEntries().forEach((key, value) -> putSnapshotEntry(copy, key, value));
        source.conditions().forEach(copy::addCondition);
        return copy;
    }

    private <T> void putSnapshotEntry(InMemoryBlackboard target, BlackboardKey<T> key, Object value) {
        target.put(key, key.type().cast(value));
    }

    private Heartbeat startHeartbeat(String runId) {
        if (!durabilityEnabled) {
            return Heartbeat.noop();
        }
        AtomicBoolean active = new AtomicBoolean(true);
        Thread thread = Thread.ofVirtual().name("actiongraph-heartbeat-" + runId).start(() -> {
            while (active.get()) {
                try {
                    Thread.sleep(heartbeatInterval.toMillis());
                    if (active.get()) {
                        suspendedRunRepository.heartbeat(runId);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (RuntimeException ex) {
                    LOGGER.debug("Heartbeat failed: runId={}", runId, ex);
                }
            }
        });
        return new Heartbeat(active, thread);
    }

    private record Selection(
            Action action,
            boolean halted,
            RunStatus status,
            boolean compensateOnHalt,
            ActionId pendingActionId,
            String message
    ) {
        static Selection action(Action action) {
            return new Selection(action, false, null, false, null, "");
        }

        static Selection halt(RunStatus status, boolean compensateOnHalt, ActionId pendingActionId, String message) {
            return new Selection(null, true, status, compensateOnHalt, pendingActionId, message);
        }
    }

    private record ActionExecutionOutcome(
            ActionResult result,
            boolean timedOut,
            int attempts,
            Map<String, String> observationTags
    ) {
        private ActionExecutionOutcome {
            observationTags = Map.copyOf(observationTags);
        }
    }

    private record AttemptResult(ActionResult result, RuntimeException exception, boolean timedOut) {
        static AttemptResult result(ActionResult result) {
            return new AttemptResult(Objects.requireNonNull(result, "result"), null, false);
        }

        static AttemptResult exception(RuntimeException exception) {
            return new AttemptResult(null, Objects.requireNonNull(exception, "exception"), false);
        }

        static AttemptResult timeout() {
            return new AttemptResult(null, null, true);
        }
    }

    private record Heartbeat(AtomicBoolean active, Thread thread) implements AutoCloseable {
        static Heartbeat noop() {
            return new Heartbeat(new AtomicBoolean(false), null);
        }

        @Override
        public void close() {
            active.set(false);
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    private static final class RunTrace {
        private final TraceRepository repository;
        private final DataMaskingPolicy maskingPolicy;
        private final ObservationSink observationSink;
        private final String runId;
        private final List<TraceEvent> buffer = new ArrayList<>();
        private long seq;
        private String previousHash;

        private RunTrace(
                TraceRepository repository,
                String runId,
                DataMaskingPolicy maskingPolicy,
                ObservationSink observationSink
        ) {
            this.repository = repository;
            this.runId = runId;
            this.maskingPolicy = maskingPolicy;
            this.observationSink = observationSink;
            List<TraceEvent> existingEvents = repository.findByRun(runId);
            this.seq = existingEvents.stream()
                    .mapToLong(TraceEvent::seq)
                    .max()
                    .orElse(0);
            this.previousHash = existingEvents.stream()
                    .max(Comparator.comparingLong(TraceEvent::seq))
                    .map(TraceEvent::hash)
                    .filter(hash -> !hash.isBlank())
                    .orElse("");
        }

        void append(TraceEventType type, ActionId actionId, String detail, Map<String, String> data) {
            long nextSeq = ++seq;
            Instant at = Instant.now();
            String maskedDetail = maskingPolicy.maskText(detail);
            Map<String, String> maskedData = maskingPolicy.maskData(data);
            String actionIdValue = actionId == null ? null : actionId.value();
            String prevHash = previousHash;
            String hash = TraceHasher.hash(
                    runId,
                    nextSeq,
                    at,
                    type,
                    actionIdValue,
                    maskedDetail,
                    maskedData,
                    prevHash
            );
            buffer.add(new TraceEvent(
                    runId,
                    nextSeq,
                    at,
                    type,
                    actionIdValue,
                    maskedDetail,
                    maskedData,
                    prevHash,
                    hash
            ));
            previousHash = hash;
        }

        void flush() {
            if (buffer.isEmpty()) {
                return;
            }
            int eventCount = buffer.size();
            long flushStartedAt = System.nanoTime();
            LOGGER.debug("Flushing trace events: runId={}, events={}", runId, eventCount);
            repository.appendAll(List.copyOf(buffer));
            observe(observationSink, ObservationEvent.timed(runId, ObservationEventType.TRACE_FLUSHED,
                    null, Map.of("events", Integer.toString(eventCount)), elapsedSince(flushStartedAt)));
            buffer.clear();
        }
    }

    public static final class Builder {
        private Planner planner = new GoapPlanner();
        private ExecutionPolicyGuard policyGuard = new DefaultPolicyGuard();
        private HumanReviewPolicy humanReviewPolicy = new PendingHumanReviewPolicy();
        private TraceRepository traceRepository = new InMemoryTraceRepository();
        private SuspendedRunRepository suspendedRunRepository = new InMemorySuspendedRunRepository();
        private DataMaskingPolicy maskingPolicy = NoopMaskingPolicy.INSTANCE;
        private ReviewAttributeContributor reviewAttributeContributor = NoopReviewAttributeContributor.INSTANCE;
        private ObservationSink observationSink = NoopObservationSink.INSTANCE;
        private int maxSteps = DEFAULT_MAX_STEPS;
        private boolean durabilityEnabled;
        private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
        private Duration defaultEventWaitTimeout = DEFAULT_EVENT_WAIT_TIMEOUT;

        public Builder planner(Planner planner) {
            this.planner = Objects.requireNonNull(planner, "planner");
            return this;
        }

        public Builder policyGuard(ExecutionPolicyGuard policyGuard) {
            this.policyGuard = Objects.requireNonNull(policyGuard, "policyGuard");
            return this;
        }

        public Builder humanReviewPolicy(HumanReviewPolicy humanReviewPolicy) {
            this.humanReviewPolicy = Objects.requireNonNull(humanReviewPolicy, "humanReviewPolicy");
            return this;
        }

        public Builder traceRepository(TraceRepository traceRepository) {
            this.traceRepository = Objects.requireNonNull(traceRepository, "traceRepository");
            return this;
        }

        public Builder suspendedRunRepository(SuspendedRunRepository suspendedRunRepository) {
            this.suspendedRunRepository = Objects.requireNonNull(suspendedRunRepository, "suspendedRunRepository");
            return this;
        }

        public Builder maskingPolicy(DataMaskingPolicy maskingPolicy) {
            this.maskingPolicy = Objects.requireNonNull(maskingPolicy, "maskingPolicy");
            return this;
        }

        public Builder reviewAttributeContributor(ReviewAttributeContributor reviewAttributeContributor) {
            this.reviewAttributeContributor = Objects.requireNonNull(reviewAttributeContributor, "reviewAttributeContributor");
            return this;
        }

        public Builder observationSink(ObservationSink observationSink) {
            this.observationSink = Objects.requireNonNull(observationSink, "observationSink");
            return this;
        }

        public Builder maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        @Experimental(
                since = "0.2.0",
                value = "Durable checkpoints are experimental until MS1 crash-recovery pilots complete."
        )
        public Builder durabilityEnabled(boolean durabilityEnabled) {
            this.durabilityEnabled = durabilityEnabled;
            return this;
        }

        @Experimental(
                since = "0.2.0",
                value = "Durable checkpoint heartbeats are experimental until MS1 crash-recovery pilots complete."
        )
        public Builder heartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = Objects.requireNonNull(heartbeatInterval, "heartbeatInterval");
            return this;
        }

        @Experimental(
                since = "0.2.0",
                value = "External event waits are experimental until MS2 event ingress pilots complete."
        )
        public Builder defaultEventWaitTimeout(Duration defaultEventWaitTimeout) {
            this.defaultEventWaitTimeout = Objects.requireNonNull(defaultEventWaitTimeout, "defaultEventWaitTimeout");
            return this;
        }

        public GoapExecutor build() {
            return new GoapExecutor(
                    planner,
                    policyGuard,
                    humanReviewPolicy,
                    traceRepository,
                    suspendedRunRepository,
                    maskingPolicy,
                    reviewAttributeContributor,
                    observationSink,
                    maxSteps,
                    durabilityEnabled,
                    heartbeatInterval,
                    defaultEventWaitTimeout
            );
        }
    }
}
