package com.actiongraph.action;

import com.actiongraph.api.Experimental;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;

import java.util.Set;

/**
 * A typed business operation that can be selected by the symbolic planner and
 * executed by the runtime.
 *
 * <p>Action implementations are part of the application contract. They should
 * be deterministic with respect to their declared preconditions/effects, avoid
 * storing per-run mutable state in fields, and be safe to invoke concurrently
 * when the containing application reuses a single {@code Action} instance.
 * Runtime-specific values should be read from {@link ExecutionContext#blackboard()}.
 *
 * <p>Null contract: implementations should not return {@code null} from any
 * method. The runtime treats thrown exceptions from {@link #execute(ExecutionContext)}
 * and {@link #compensate(ExecutionContext)} as failed execution or failed
 * best-effort compensation respectively.
 */
public interface Action {
    /**
     * Stable identifier used in plans, traces, compensation stacks, and
     * registries. The value must be unique inside the supplied action set.
     *
     * @return non-null stable action id
     */
    ActionId id();

    /**
     * Declares Blackboard value types expected before execution.
     *
     * @return non-null set of required input types
     */
    Set<Class<?>> inputTypes();

    /**
     * Declares Blackboard value types this action may produce.
     *
     * @return non-null set of output types
     */
    Set<Class<?>> outputTypes();

    /**
     * Symbolic facts that must be present before the planner may select this
     * action.
     *
     * @return non-null set of symbolic preconditions
     */
    Set<Condition> preconditions();

    /**
     * Symbolic facts the planner may assume after this action succeeds.
     *
     * @return non-null set of symbolic effects
     */
    Set<Condition> effects();

    /**
     * Relative planning cost. The default planner is deterministic BFS today,
     * but this value is part of the stable contract for future cost-aware
     * planners.
     *
     * @return non-negative relative cost
     */
    int cost();

    /**
     * Business risk level surfaced in trace and human-review requests.
     *
     * @return non-null risk level
     */
    ActionRiskLevel riskLevel();

    /**
     * Indicates whether this action must be routed through human review before
     * execution when policy allows review escalation.
     *
     * @return {@code true} when human review is required
     */
    boolean requiresHumanReview();

    /**
     * Runtime execution controls for this action.
     *
     * <p>The default policy preserves legacy behavior: a single attempt, no
     * backoff, and no timeout. Declaring more than one attempt is an application
     * contract that {@link #execute(ExecutionContext)} is idempotent.
     *
     * @return non-null execution policy
     */
    @Experimental(
            since = "0.1.0",
            value = "Retry and timeout execution policy is experimental until idempotency conventions are proven in pilots."
    )
    default ActionExecutionPolicy executionPolicy() {
        return ActionExecutionPolicy.none();
    }

    /**
     * Value-dependent guard evaluated immediately before execution. Unlike
     * {@link #preconditions()}, this method may inspect concrete Blackboard
     * values and is intentionally invisible to the planner.
     *
     * @param blackboard current run Blackboard; never {@code null}
     * @return {@code true} when execution may proceed, {@code false} to exclude
     * the action and replan
     */
    default boolean runtimeGuard(Blackboard blackboard) {
        return true;
    }

    /**
     * Executes the business operation. Implementations may read and write
     * domain systems and may update the Blackboard through the supplied context.
     *
     * @param context execution context for the current run; never {@code null}
     * @return non-null execution result
     */
    ActionResult execute(ExecutionContext context);

    /**
     * Best-effort compensation invoked in reverse execution order when a run
     * fails or is denied after this action has succeeded.
     *
     * @param context execution context for the current run; never {@code null}
     * @return non-null compensation result
     */
    default CompensationResult compensate(ExecutionContext context) {
        return CompensationResult.noop();
    }
}
