package com.actiongraph.llm.evals;

import com.actiongraph.api.Experimental;

/**
 * Acceptance thresholds for a golden-set evaluation.
 */
@Experimental(
        since = "0.2.0",
        value = "Golden-set evaluation contracts are experimental until STD3 pilots settle."
)
public record Thresholds(
        double goalTypeAccuracy,
        double parametersAccuracy,
        double clarificationAccuracy
) {
    public Thresholds {
        assertRate(goalTypeAccuracy, "goalTypeAccuracy");
        assertRate(parametersAccuracy, "parametersAccuracy");
        assertRate(clarificationAccuracy, "clarificationAccuracy");
    }

    public static Thresholds exact() {
        return new Thresholds(1.0d, 1.0d, 1.0d);
    }

    public static Thresholds of(double goalTypeAccuracy, double parametersAccuracy) {
        return new Thresholds(goalTypeAccuracy, parametersAccuracy, 1.0d);
    }

    public static Thresholds of(
            double goalTypeAccuracy,
            double parametersAccuracy,
            double clarificationAccuracy
    ) {
        return new Thresholds(goalTypeAccuracy, parametersAccuracy, clarificationAccuracy);
    }

    boolean accepts(EvalReport report) {
        return report.goalTypeAccuracy() >= goalTypeAccuracy
                && report.parametersAccuracy() >= parametersAccuracy
                && report.clarificationAccuracy() >= clarificationAccuracy;
    }

    private static void assertRate(double rate, String name) {
        if (Double.isNaN(rate) || rate < 0.0d || rate > 1.0d) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
