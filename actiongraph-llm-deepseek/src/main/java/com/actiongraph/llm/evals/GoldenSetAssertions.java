package com.actiongraph.llm.evals;

import com.actiongraph.api.Experimental;
import com.actiongraph.interpretation.GoalInterpreter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Lightweight JUnit-friendly assertion helpers for golden-set evaluation.
 */
@Experimental(
        since = "0.2.0",
        value = "Golden-set evaluation contracts are experimental until STD3 pilots settle."
)
public final class GoldenSetAssertions {
    private GoldenSetAssertions() {
    }

    public static EvalReport assertMeets(
            GoalInterpreter interpreter,
            Path goldenSet,
            Thresholds thresholds
    ) {
        Objects.requireNonNull(thresholds, "thresholds");
        EvalReport report = new GoldenSetEvalRunner().evaluate(interpreter, goldenSet);
        if (!thresholds.accepts(report)) {
            throw new AssertionError("ActionGraph golden-set evaluation did not meet thresholds.\n\n"
                    + report.toMarkdown());
        }
        return report;
    }
}
