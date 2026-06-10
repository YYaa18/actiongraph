package com.actiongraph.action;

import com.actiongraph.planning.Condition;

import java.util.Arrays;
import java.util.List;

public record ActionResult(boolean success, String message, List<Condition> producedConditions) {
    public ActionResult {
        message = message == null ? "" : message;
        producedConditions = producedConditions == null ? List.of() : List.copyOf(producedConditions);
    }

    public static ActionResult ok(Condition... conditions) {
        return new ActionResult(true, "ok", Arrays.asList(conditions));
    }

    public static ActionResult fail(String message) {
        return new ActionResult(false, message, List.of());
    }
}
