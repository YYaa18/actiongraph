package com.actiongraph.runtime;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.planning.Plan;
import com.actiongraph.planning.Planner;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.ExecutionPolicyGuard;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.HumanReviewRequest;
import com.actiongraph.policy.HumanReviewResult;
import com.actiongraph.policy.PendingHumanReviewPolicy;
import com.actiongraph.policy.PolicyDecision;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceRepository;

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
import java.util.stream.Collectors;

public final class GoapExecutor implements Executor {
    public static final int DEFAULT_MAX_STEPS = 64;

    private final Planner planner;
    private final ExecutionPolicyGuard policyGuard;
    private final HumanReviewPolicy humanReviewPolicy;
    private final TraceRepository traceRepository;
    private final SuspendedRunRepository suspendedRunRepository;
    private final int maxSteps;

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
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps must be > 0");
        }
        this.planner = Objects.requireNonNull(planner, "planner");
        this.policyGuard = Objects.requireNonNull(policyGuard, "policyGuard");
        this.humanReviewPolicy = Objects.requireNonNull(humanReviewPolicy, "humanReviewPolicy");
        this.traceRepository = Objects.requireNonNull(traceRepository, "traceRepository");
        this.suspendedRunRepository = Objects.requireNonNull(suspendedRunRepository, "suspendedRunRepository");
        this.maxSteps = maxSteps;
    }

    public TraceRepository traceRepository() {
        return traceRepository;
    }

    public SuspendedRunRepository suspendedRunRepository() {
        return suspendedRunRepository;
    }

    @Override
    public RunResult run(Goal goal, Blackboard initial, Collection<Action> actions, ActionRegistry registry) {
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(registry, "registry");

        String runId = UUID.randomUUID().toString();
        RunTrace trace = new RunTrace(traceRepository, runId);
        trace.append(TraceEventType.RUN_STARTED, null, "Run started", Map.of(
                "goal", goal.name(),
                "targetConditions", conditionKeys(goal.targetConditions())
        ));
        return runLoop(
                runId,
                goal,
                initial,
                actions,
                registry,
                new ArrayList<>(),
                new ArrayDeque<>(),
                trace
        );
    }

    public RunResult resume(String runId, Collection<Action> actions, ActionRegistry registry) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(registry, "registry");

        SuspendedRun suspendedRun = suspendedRunRepository.claimForResume(runId)
                .orElseThrow(() -> new SuspendedRunNotClaimableException(runId));
        RunTrace trace = new RunTrace(traceRepository, runId);
        trace.append(TraceEventType.RUN_RESUMED, suspendedRun.pendingActionId(), "Run resumed", Map.of(
                "pendingActionId", suspendedRun.pendingActionId().value()
        ));
        return runLoop(
                runId,
                suspendedRun.goal(),
                suspendedRun.blackboard(),
                actions,
                registry,
                new ArrayList<>(suspendedRun.executedActions()),
                rehydrateCompensationStack(suspendedRun, registry),
                trace
        );
    }

    private RunResult runLoop(
            String runId,
            Goal goal,
            Blackboard blackboard,
            Collection<Action> actions,
            ActionRegistry registry,
            List<ActionId> executedActionIds,
            Deque<Action> compensationStack,
            RunTrace trace
    ) {
        ExecutionContext context = new DefaultExecutionContext(blackboard, traceRepository, runId);
        List<Action> allActions = List.copyOf(actions);

        for (int step = 0; step < maxSteps; step++) {
            Set<Condition> state = blackboard.conditions();
            if (goal.isSatisfiedBy(state)) {
                trace.append(TraceEventType.GOAL_SATISFIED, null, "Goal satisfied", Map.of(
                        "conditions", conditionKeys(state)
                ));
                return finish(trace, runId, RunStatus.COMPLETED, blackboard, executedActionIds, "Goal satisfied");
            }

            Selection selection = selectNextAction(runId, goal, state, blackboard, allActions, registry, trace);
            if (selection.halted()) {
                if (selection.status() == RunStatus.SUSPENDED_PENDING_REVIEW) {
                    trace.flush();
                    saveSuspendedRun(runId, goal, blackboard, executedActionIds,
                            compensationStack, selection.pendingActionId(), selection.message());
                    return finish(trace, runId, selection.status(), blackboard, executedActionIds, selection.message());
                }
                if (selection.compensateOnHalt()) {
                    boolean compensated = compensateAll(compensationStack, context, trace);
                    if (!compensated) {
                        return finish(trace, runId, RunStatus.FAILED_COMPENSATION_INCOMPLETE,
                                blackboard, executedActionIds, selection.message());
                    }
                }
                return finish(trace, runId, selection.status(), blackboard, executedActionIds, selection.message());
            }

            Action action = selection.action();
            Map<BlackboardKey<?>, Object> beforeObjects = blackboard.snapshotEntries();
            trace.append(TraceEventType.ACTION_STARTED, action.id(), "Action started", Map.of(
                    "riskLevel", action.riskLevel().name()
            ));

            ActionResult result;
            try {
                result = action.execute(context);
            } catch (Exception ex) {
                result = ActionResult.fail("Exception executing " + action.id().value() + ": " + ex.getMessage());
            }

            if (result.success()) {
                Set<Condition> addedConditions = new LinkedHashSet<>(action.effects());
                addedConditions.addAll(result.producedConditions());
                addedConditions.forEach(blackboard::addCondition);
                executedActionIds.add(action.id());
                compensationStack.push(action);

                trace.append(TraceEventType.ACTION_SUCCEEDED, action.id(), result.message(), Map.of(
                        "conditions", conditionKeys(addedConditions)
                ));
                traceBlackboardUpdate(trace, action, beforeObjects, blackboard.snapshotEntries(), addedConditions);
            } else {
                trace.append(TraceEventType.ACTION_FAILED, action.id(), result.message(), Map.of());
                boolean compensated = compensateAll(compensationStack, context, trace);
                RunStatus status = compensated
                        ? RunStatus.FAILED_COMPENSATED
                        : RunStatus.FAILED_COMPENSATION_INCOMPLETE;
                return finish(trace, runId, status, blackboard, executedActionIds, result.message());
            }
        }

        trace.append(TraceEventType.NO_PLAN, null, "Max executor steps exceeded", Map.of(
                "maxSteps", Integer.toString(maxSteps)
        ));
        return finish(trace, runId, RunStatus.HALTED_UNREACHABLE, blackboard, executedActionIds,
                "Max executor steps exceeded");
    }

    private Selection selectNextAction(
            String runId,
            Goal goal,
            Set<Condition> state,
            Blackboard blackboard,
            List<Action> allActions,
            ActionRegistry registry,
            RunTrace trace
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
                return Selection.halt(RunStatus.HALTED_UNREACHABLE, false, guardBlockedAction, detail);
            }

            Plan plan = planOpt.get();
            trace.append(TraceEventType.PLAN_GENERATED, null, "Plan generated", Map.of(
                    "steps", plan.steps().stream()
                            .map(step -> step.actionId().value())
                            .collect(Collectors.joining(","))
            ));

            ActionId actionId = plan.steps().getFirst().actionId();
            Optional<Action> actionOpt = registry.byId(actionId);
            if (actionOpt.isEmpty()) {
                String detail = "Planned action is not registered: " + actionId.value();
                trace.append(TraceEventType.NO_PLAN, actionId, detail, Map.of());
                return Selection.halt(RunStatus.HALTED_UNREACHABLE, false, actionId, detail);
            }

            Action action = actionOpt.get();
            PolicyDecision decision = policyGuard.evaluate(action, blackboard);
            trace.append(TraceEventType.POLICY_EVALUATED, action.id(), "Policy evaluated", Map.of(
                    "decision", decision.name()
            ));
            if (decision == PolicyDecision.DENY) {
                String detail = "Policy denied action: " + action.id().value();
                trace.append(TraceEventType.POLICY_DENIED, action.id(), detail, Map.of());
                return Selection.halt(RunStatus.DENIED_BY_POLICY, true, action.id(), detail);
            }
            if (decision == PolicyDecision.REQUIRES_HUMAN_REVIEW) {
                trace.append(TraceEventType.HUMAN_REVIEW_REQUESTED, action.id(),
                        "Human review requested", Map.of(
                                "riskLevel", action.riskLevel().name(),
                                "planPreview", plan.steps().stream()
                                        .map(step -> step.actionId().value())
                                        .collect(Collectors.joining(","))
                        ));
                trace.flush();
                HumanReviewResult review = humanReviewPolicy.review(new HumanReviewRequest(
                        runId,
                        action.id(),
                        action.riskLevel(),
                        action.requiresHumanReview(),
                        plan,
                        state,
                        objectPreview(blackboard.snapshotEntries())
                ));
                trace.append(TraceEventType.HUMAN_REVIEW_DECIDED, action.id(),
                        review.message(), Map.of(
                                "decision", review.decision().name(),
                                "reviewer", review.reviewer()
                        ));
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
                continue;
            }

            return Selection.action(action);
        }
    }

    private boolean compensateAll(Deque<Action> compensationStack, ExecutionContext context, RunTrace trace) {
        trace.flush();
        boolean allOk = true;
        while (!compensationStack.isEmpty()) {
            Action action = compensationStack.pop();
            try {
                CompensationResult result = action.compensate(context);
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
                trace.append(TraceEventType.COMPENSATION_ERROR, action.id(), ex.getMessage(), Map.of(
                        "exceptionType", ex.getClass().getName()
                ));
                trace.flush();
            }
        }
        return allOk;
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
        suspendedRunRepository.save(new SuspendedRun(
                runId,
                goal,
                blackboard,
                executedActionIds,
                compensationStack.stream().map(Action::id).toList(),
                pendingActionId,
                message
        ));
    }

    private Deque<Action> rehydrateCompensationStack(SuspendedRun suspendedRun, ActionRegistry registry) {
        Deque<Action> stack = new ArrayDeque<>();
        for (ActionId actionId : suspendedRun.compensationStack()) {
            Action action = registry.byId(actionId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Compensation action is not registered: " + actionId.value()));
            stack.addLast(action);
        }
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
            String message
    ) {
        if (status == RunStatus.SUSPENDED_PENDING_REVIEW) {
            trace.append(TraceEventType.RUN_SUSPENDED, null, message, Map.of("status", status.name()));
        } else {
            suspendedRunRepository.delete(runId);
            trace.append(TraceEventType.RUN_ENDED, null, message, Map.of("status", status.name()));
        }
        trace.flush();
        return new RunResult(runId, status, blackboard.conditions(), executedActions, message);
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

    private static final class RunTrace {
        private final TraceRepository repository;
        private final String runId;
        private final List<TraceEvent> buffer = new ArrayList<>();
        private long seq;

        private RunTrace(TraceRepository repository, String runId) {
            this.repository = repository;
            this.runId = runId;
            this.seq = repository.findByRun(runId).stream()
                    .mapToLong(TraceEvent::seq)
                    .max()
                    .orElse(0);
        }

        void append(TraceEventType type, ActionId actionId, String detail, Map<String, String> data) {
            buffer.add(new TraceEvent(
                    runId,
                    ++seq,
                    Instant.now(),
                    type,
                    actionId == null ? null : actionId.value(),
                    detail,
                    data
            ));
        }

        void flush() {
            if (buffer.isEmpty()) {
                return;
            }
            repository.appendAll(List.copyOf(buffer));
            buffer.clear();
        }
    }
}
