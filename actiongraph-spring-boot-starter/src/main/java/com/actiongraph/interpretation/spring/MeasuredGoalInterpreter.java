package com.actiongraph.interpretation.spring;

import com.actiongraph.api.Experimental;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.MissingField;
import com.actiongraph.interpretation.sampling.InterpretationSample;
import com.actiongraph.interpretation.sampling.InterpretationSampleRepository;
import com.actiongraph.interpretation.sampling.InterpretationSampleTracker;
import com.actiongraph.llm.FallbackGoalInterpreter;
import com.actiongraph.observability.ObservationEvent;
import com.actiongraph.observability.ObservationEventType;
import com.actiongraph.observability.ObservationSink;
import com.actiongraph.policy.DataMaskingPolicy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;

/**
 * Goal interpreter decorator that records interpretation metrics and sanitized
 * quality samples without changing interpreter semantics.
 */
@Experimental(
        since = "0.2.0",
        value = "Interpretation quality measurement is experimental until STD3 pilots settle."
)
public final class MeasuredGoalInterpreter implements GoalInterpreter, InterpretationSampleTracker {
    private final GoalInterpreter delegate;
    private final ObservationSink observationSink;
    private final InterpretationSampleRepository sampleRepository;
    private final DataMaskingPolicy maskingPolicy;
    private final boolean metricsEnabled;
    private final double samplingRate;
    private final DoubleSupplier random;
    private final ThreadLocal<String> lastSampleId = new ThreadLocal<>();

    public MeasuredGoalInterpreter(
            GoalInterpreter delegate,
            ObservationSink observationSink,
            InterpretationSampleRepository sampleRepository,
            DataMaskingPolicy maskingPolicy,
            boolean metricsEnabled,
            double samplingRate
    ) {
        this(delegate, observationSink, sampleRepository, maskingPolicy, metricsEnabled, samplingRate, Math::random);
    }

    public MeasuredGoalInterpreter(
            GoalInterpreter delegate,
            ObservationSink observationSink,
            InterpretationSampleRepository sampleRepository,
            DataMaskingPolicy maskingPolicy,
            boolean metricsEnabled,
            double samplingRate,
            DoubleSupplier random
    ) {
        if (samplingRate < 0.0d || samplingRate > 1.0d) {
            throw new IllegalArgumentException("samplingRate must be between 0 and 1");
        }
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.observationSink = Objects.requireNonNull(observationSink, "observationSink");
        this.sampleRepository = Objects.requireNonNull(sampleRepository, "sampleRepository");
        this.maskingPolicy = Objects.requireNonNull(maskingPolicy, "maskingPolicy");
        this.metricsEnabled = metricsEnabled;
        this.samplingRate = samplingRate;
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public GoalInterpretation interpret(String input) {
        return interpret(input, GoalParameters.empty());
    }

    @Override
    public GoalInterpretation interpret(String input, GoalParameters knownParameters) {
        lastSampleId.remove();
        AtomicBoolean fallbackUsed = new AtomicBoolean(false);
        long started = System.nanoTime();
        GoalInterpretation interpretation = delegate instanceof FallbackGoalInterpreter fallback
                ? fallback.interpret(input, knownParameters, fallbackUsed::set)
                : delegate.interpret(input, knownParameters);
        long durationNanos = System.nanoTime() - started;
        boolean parseFailure = parseFailure(interpretation);
        String outcome = outcome(interpretation);
        if (metricsEnabled) {
            observationSink.observe(ObservationEvent.timed(
                    "interpretation-" + UUID.randomUUID(),
                    ObservationEventType.INTERPRETATION_FINISHED,
                    null,
                    tags(interpretation, outcome, fallbackUsed.get(), parseFailure),
                    durationNanos
            ));
        }
        if (shouldSample()) {
            InterpretationSample sample = sample(input, interpretation, outcome, fallbackUsed.get(), parseFailure);
            sampleRepository.save(sample);
            lastSampleId.set(sample.id());
        }
        return interpretation;
    }

    @Override
    public Optional<String> lastSampleId() {
        return Optional.ofNullable(lastSampleId.get());
    }

    @Override
    public void attachRunId(String sampleId, String runId) {
        sampleRepository.attachRunId(sampleId, runId);
        lastSampleId.remove();
    }

    private boolean shouldSample() {
        return samplingRate > 0.0d && random.getAsDouble() < samplingRate;
    }

    private InterpretationSample sample(
            String input,
            GoalInterpretation interpretation,
            String outcome,
            boolean fallbackUsed,
            boolean parseFailure
    ) {
        return new InterpretationSample(
                UUID.randomUUID().toString(),
                Instant.now(),
                maskingPolicy.maskText(input),
                outcome,
                interpretation.goalType().value(),
                missingFields(interpretation),
                fallbackUsed,
                parseFailure,
                null,
                false
        );
    }

    private static Map<String, String> tags(
            GoalInterpretation interpretation,
            String outcome,
            boolean fallbackUsed,
            boolean parseFailure
    ) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("outcome", outcome);
        tags.put("goalType", interpretation.goalType().value());
        tags.put("fallback_used", Boolean.toString(fallbackUsed));
        tags.put("parse_failure", Boolean.toString(parseFailure));
        tags.put("success", Boolean.toString(interpretation.isReady()));
        return Map.copyOf(tags);
    }

    private static String outcome(GoalInterpretation interpretation) {
        if ("unknown".equalsIgnoreCase(interpretation.goalType().value())) {
            return "unknown";
        }
        return interpretation.isReady() ? "ready" : "clarification";
    }

    private static boolean parseFailure(GoalInterpretation interpretation) {
        return interpretation.missingFields().stream()
                .map(MissingField::name)
                .anyMatch("validGoalInterpretation"::equals);
    }

    private static Set<String> missingFields(GoalInterpretation interpretation) {
        return interpretation.missingFields().stream()
                .map(MissingField::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
