package com.actiongraph.llm.evals;

import com.actiongraph.api.Experimental;

import java.util.List;
import java.util.Locale;

/**
 * Aggregate interpretation evaluation result.
 */
@Experimental(
        since = "0.2.0",
        value = "Golden-set evaluation contracts are experimental until STD3 pilots settle."
)
public record EvalReport(
        int total,
        int goalTypeCorrect,
        int parametersCorrect,
        int clarificationCorrect,
        List<CaseDiff> failures
) {
    public EvalReport {
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public double goalTypeAccuracy() {
        return ratio(goalTypeCorrect);
    }

    public double parametersAccuracy() {
        return ratio(parametersCorrect);
    }

    public double clarificationAccuracy() {
        return ratio(clarificationCorrect);
    }

    public boolean exact() {
        return failures.isEmpty();
    }

    public String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ActionGraph Interpretation Eval Report\n\n");
        markdown.append("| Metric | Value |\n");
        markdown.append("|---|---:|\n");
        markdown.append("| Total | ").append(total).append(" |\n");
        markdown.append("| Goal type accuracy | ").append(percent(goalTypeAccuracy())).append(" |\n");
        markdown.append("| Parameter accuracy | ").append(percent(parametersAccuracy())).append(" |\n");
        markdown.append("| Clarification accuracy | ").append(percent(clarificationAccuracy())).append(" |\n");
        markdown.append("| Failures | ").append(failures.size()).append(" |\n\n");
        if (!failures.isEmpty()) {
            markdown.append("## Failures\n\n");
            for (CaseDiff failure : failures) {
                markdown.append("### ").append(escapeHeading(failure.input())).append("\n\n");
                markdown.append("- Expected: `").append(failure.expected()).append("`\n");
                markdown.append("- Actual: `").append(failure.actual()).append("`\n");
                markdown.append("- Differences: ").append(String.join("; ", failure.differences())).append("\n\n");
            }
        }
        return markdown.toString();
    }

    private double ratio(int correct) {
        if (total == 0) {
            return 1.0d;
        }
        return correct / (double) total;
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0d);
    }

    private static String escapeHeading(String value) {
        return value.replace("\n", " ").replace("#", "\\#");
    }
}
