package com.actiongraph.llm.evals;

import com.actiongraph.api.Experimental;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Executes JSONL golden-set cases against any {@link GoalInterpreter}.
 */
@Experimental(
        since = "0.2.0",
        value = "Golden-set evaluation contracts are experimental until STD3 pilots settle."
)
public final class GoldenSetEvalRunner {
    private static final Path DEFAULT_REPORT_DIR = Path.of("build", "reports", "actiongraph", "evals");

    private final ObjectMapper objectMapper;
    private final Path reportDirectory;

    public GoldenSetEvalRunner() {
        this(new ObjectMapper(), DEFAULT_REPORT_DIR);
    }

    public GoldenSetEvalRunner(Path reportDirectory) {
        this(new ObjectMapper(), reportDirectory);
    }

    public GoldenSetEvalRunner(ObjectMapper objectMapper, Path reportDirectory) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.reportDirectory = Objects.requireNonNull(reportDirectory, "reportDirectory");
    }

    public EvalReport evaluate(GoalInterpreter interpreter, Path goldenSet) {
        Objects.requireNonNull(interpreter, "interpreter");
        Objects.requireNonNull(goldenSet, "goldenSet");
        List<GoldenCase> cases = readCases(goldenSet);
        List<CaseDiff> failures = new ArrayList<>();
        int goalTypeCorrect = 0;
        int parametersCorrect = 0;
        int clarificationCorrect = 0;

        for (GoldenCase goldenCase : cases) {
            GoalInterpretation interpretation = interpreter.interpret(goldenCase.input(), GoalParameters.empty());
            ActualInterpretation actual = ActualInterpretation.from(interpretation);
            CaseEvaluation evaluation = evaluateCase(goldenCase, actual);
            if (evaluation.goalTypeCorrect()) {
                goalTypeCorrect++;
            }
            if (evaluation.parametersCorrect()) {
                parametersCorrect++;
            }
            if (evaluation.clarificationCorrect()) {
                clarificationCorrect++;
            }
            if (!evaluation.differences().isEmpty()) {
                failures.add(new CaseDiff(
                        goldenCase.input(),
                        goldenCase.expect(),
                        actual,
                        evaluation.differences()
                ));
            }
        }

        EvalReport report = new EvalReport(
                cases.size(),
                goalTypeCorrect,
                parametersCorrect,
                clarificationCorrect,
                failures
        );
        writeReport(goldenSet, report);
        return report;
    }

    public List<GoldenCase> readCases(Path goldenSet) {
        try {
            List<GoldenCase> cases = new ArrayList<>();
            int lineNumber = 0;
            for (String line : Files.readAllLines(goldenSet, StandardCharsets.UTF_8)) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                try {
                    cases.add(objectMapper.readValue(trimmed, GoldenCase.class));
                } catch (Exception ex) {
                    throw new IllegalArgumentException(
                            "Invalid golden case at " + goldenSet + ":" + lineNumber, ex);
                }
            }
            if (cases.isEmpty()) {
                throw new IllegalArgumentException("Golden set must contain at least one case: " + goldenSet);
            }
            return List.copyOf(cases);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Cannot read golden set: " + goldenSet, ex);
        }
    }

    private CaseEvaluation evaluateCase(GoldenCase goldenCase, ActualInterpretation actual) {
        Expectation expected = goldenCase.expect();
        List<String> differences = new ArrayList<>();

        boolean goalTypeCorrect = goalTypeCorrect(expected, actual);
        if (!goalTypeCorrect) {
            differences.add("goalType expected " + expectedGoalType(expected) + " but was " + actual.goalType());
        }

        boolean parametersCorrect = expected.parameters().equals(actual.parameters());
        if (!parametersCorrect) {
            differences.add("parameters expected " + expected.parameters() + " but were " + actual.parameters());
        }

        boolean clarificationCorrect = expected.clarification() == actual.clarification();
        if (!clarificationCorrect) {
            differences.add("clarification expected " + expected.clarification()
                    + " but was " + actual.clarification());
        }

        if (!expected.missingFields().isEmpty()) {
            boolean missingFieldsCorrect = expected.missingFields().equals(actual.missingFields());
            if (!missingFieldsCorrect) {
                differences.add("missingFields expected " + sorted(expected.missingFields())
                        + " but were " + sorted(actual.missingFields()));
            }
            clarificationCorrect = clarificationCorrect && missingFieldsCorrect;
        }

        return new CaseEvaluation(goalTypeCorrect, parametersCorrect, clarificationCorrect, differences);
    }

    private boolean goalTypeCorrect(Expectation expected, ActualInterpretation actual) {
        if (expected.unknownGoal()) {
            return actual.unknownGoal();
        }
        if (expected.goalType() == null || expected.goalType().isBlank()) {
            return true;
        }
        return expected.goalType().equals(actual.goalType());
    }

    private String expectedGoalType(Expectation expected) {
        return expected.unknownGoal() ? "unknown" : expected.goalType();
    }

    private static List<String> sorted(java.util.Set<String> values) {
        return values.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private void writeReport(Path goldenSet, EvalReport report) {
        try {
            Files.createDirectories(reportDirectory);
            String fileName = goldenSet.getFileName().toString().replaceAll("[^A-Za-z0-9_.-]", "_") + ".md";
            Files.writeString(reportDirectory.resolve(fileName), report.toMarkdown(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot write eval report", ex);
        }
    }

    private record CaseEvaluation(
            boolean goalTypeCorrect,
            boolean parametersCorrect,
            boolean clarificationCorrect,
            List<String> differences
    ) {
        private CaseEvaluation {
            differences = List.copyOf(differences);
        }
    }
}
