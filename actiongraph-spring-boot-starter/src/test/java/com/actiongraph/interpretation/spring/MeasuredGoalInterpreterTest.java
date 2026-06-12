package com.actiongraph.interpretation.spring;

import com.actiongraph.interpretation.ClarificationQuestion;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.MissingField;
import com.actiongraph.interpretation.sampling.InMemoryInterpretationSampleRepository;
import com.actiongraph.llm.FallbackGoalInterpreter;
import com.actiongraph.llm.LlmClientException;
import com.actiongraph.observability.ObservationEvent;
import com.actiongraph.policy.DataMaskingPolicy;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MeasuredGoalInterpreterTest {
    private static final GoalType READY = new GoalType("readyGoal");
    private static final GoalType UNKNOWN = new GoalType("unknown");

    @Test
    void recordsMetricsAndMaskedSamplesForInterpretationOutcomes() {
        List<ObservationEvent> events = new ArrayList<>();
        InMemoryInterpretationSampleRepository repository = new InMemoryInterpretationSampleRepository();
        MeasuredGoalInterpreter interpreter = measured(
                new OutcomeInterpreter(),
                events,
                repository,
                true,
                1.0d
        );

        interpreter.interpret("ready 13812345678");
        interpreter.interpret("missing 13812345678");
        interpreter.interpret("weather 13812345678");

        assertThat(events)
                .extracting(event -> event.tags().get("outcome"))
                .containsExactly("ready", "clarification", "unknown");
        assertThat(repository.findRecent(10))
                .hasSize(3)
                .allSatisfy(sample -> {
                    assertThat(sample.maskedInput()).doesNotContain("13812345678");
                    assertThat(sample.maskedInput()).contains("138****5678");
                });
    }

    @Test
    void recordsFallbackAndParseFailureDimensions() {
        List<ObservationEvent> events = new ArrayList<>();
        InMemoryInterpretationSampleRepository repository = new InMemoryInterpretationSampleRepository();
        GoalInterpreter fallbackChain = new FallbackGoalInterpreter(
                new ThrowingInterpreter(),
                new OutcomeInterpreter()
        );
        MeasuredGoalInterpreter fallbackMeasured = measured(fallbackChain, events, repository, true, 1.0d);

        fallbackMeasured.interpret("ready 13812345678");

        assertThat(events.get(0).tags())
                .containsEntry("fallback_used", "true")
                .containsEntry("parse_failure", "false");
        assertThat(repository.findRecent(1).get(0).fallbackUsed()).isTrue();

        MeasuredGoalInterpreter parseFailureMeasured = measured(
                new ParseFailureInterpreter(),
                events,
                repository,
                true,
                1.0d
        );

        parseFailureMeasured.interpret("broken 13812345678");

        assertThat(events.get(1).tags())
                .containsEntry("fallback_used", "false")
                .containsEntry("parse_failure", "true");
        assertThat(repository.findRecent(1).get(0).parseFailure()).isTrue();
    }

    @Test
    void rateZeroDoesNotStoreSamples() {
        InMemoryInterpretationSampleRepository repository = new InMemoryInterpretationSampleRepository();
        MeasuredGoalInterpreter interpreter = measured(
                new OutcomeInterpreter(),
                new ArrayList<>(),
                repository,
                true,
                0.0d
        );

        interpreter.interpret("ready 13812345678");

        assertThat(repository.findRecent(10)).isEmpty();
    }

    private static MeasuredGoalInterpreter measured(
            GoalInterpreter delegate,
            List<ObservationEvent> events,
            InMemoryInterpretationSampleRepository repository,
            boolean metrics,
            double samplingRate
    ) {
        return new MeasuredGoalInterpreter(
                delegate,
                events::add,
                repository,
                new PhoneMaskingPolicy(),
                metrics,
                samplingRate,
                () -> 0.0d
        );
    }

    private static final class OutcomeInterpreter implements GoalInterpreter {
        @Override
        public GoalInterpretation interpret(String input) {
            return interpret(input, GoalParameters.empty());
        }

        @Override
        public GoalInterpretation interpret(String input, GoalParameters knownParameters) {
            if (input.startsWith("weather")) {
                return GoalInterpretation.needsClarification(
                        UNKNOWN,
                        GoalParameters.empty(),
                        Set.of(new MissingField("supportedGoal")),
                        new ClarificationQuestion("Unsupported goal.")
                );
            }
            if (input.startsWith("missing")) {
                return GoalInterpretation.needsClarification(
                        READY,
                        GoalParameters.empty(),
                        Set.of(new MissingField("id")),
                        new ClarificationQuestion("Which id?")
                );
            }
            return GoalInterpretation.ready(
                    READY,
                    GoalParameters.of(Map.of("id", "I-1")),
                    new Goal("readyGoal", Set.of(Condition.of("test", "DONE")))
            );
        }
    }

    private static final class ThrowingInterpreter implements GoalInterpreter {
        @Override
        public GoalInterpretation interpret(String input) {
            throw new LlmClientException("network down");
        }
    }

    private static final class ParseFailureInterpreter implements GoalInterpreter {
        @Override
        public GoalInterpretation interpret(String input) {
            return GoalInterpretation.needsClarification(
                    UNKNOWN,
                    GoalParameters.empty(),
                    Set.of(new MissingField("validGoalInterpretation")),
                    new ClarificationQuestion("Could not parse.")
            );
        }
    }

    private static final class PhoneMaskingPolicy implements DataMaskingPolicy {
        @Override
        public String maskText(String text) {
            return text == null ? "" : text.replace("13812345678", "138****5678");
        }

        @Override
        public Map<String, String> maskData(Map<String, String> data) {
            return data;
        }
    }
}
