package com.actiongraph.action;

import com.actiongraph.planning.Condition;

import java.util.Arrays;
import java.util.List;

/**
 * Result returned by an Action execution.
 *
 * <p>A successful result allows the runtime to add declared effects and
 * produced conditions to the Blackboard. A failed result triggers reverse
 * compensation for previously successful actions. The message is stored in
 * trace and should not contain unmasked sensitive data.
 *
 * @param success whether the action completed successfully
 * @param message human-readable detail for trace; {@code null} becomes empty
 * @param producedConditions additional symbolic facts produced at runtime
 */
public record ActionResult(boolean success, String message, List<Condition> producedConditions) {
    public ActionResult {
        message = message == null ? "" : message;
        producedConditions = producedConditions == null ? List.of() : List.copyOf(producedConditions);
    }

    public static ActionResult ok(Condition... conditions) {
        return new ActionResult(true, "ok", Arrays.asList(conditions));
    }

    /**
     * Creates a failed result. Returning this is preferred over throwing for
     * expected business failures because the message is traceable and
     * compensation semantics remain explicit.
     *
     * @param message failure detail
     * @return failed action result
     */
    public static ActionResult fail(String message) {
        return new ActionResult(false, message, List.of());
    }
}
