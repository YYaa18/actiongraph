package com.actiongraph.runtime.api;

import com.actiongraph.ActionGraph;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.sampling.InterpretationSampleTracker;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunResult;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Control-plane HTTP adapter service.
 *
 * <p>Application code should prefer the root {@link ActionGraph} facade. This
 * service keeps the runtime DTO/disposition shape used by HTTP adapters.
 */
public final class ActionGraphRuntimeApiService implements ActionGraphRuntimeOperations {
    private final ActionGraph actionGraph;
    private final GoalInterpreter interpreter;

    public ActionGraphRuntimeApiService(ActionGraph actionGraph, GoalInterpreter interpreter) {
        this.actionGraph = Objects.requireNonNull(actionGraph, "actionGraph");
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter");
    }

    public ActionGraphRuntimeApiService(
            GoalInterpreter interpreter,
            GoalBlackboardSeederRegistry seeders,
            GoapExecutor executor,
            ActionRegistry registry
    ) {
        this(interpreter, seeders, executor, registry, InMemoryBlackboard::new);
    }

    public ActionGraphRuntimeApiService(
            GoalInterpreter interpreter,
            GoalBlackboardSeederRegistry seeders,
            GoapExecutor executor,
            ActionRegistry registry,
            Supplier<? extends Blackboard> blackboardFactory
    ) {
        this(ActionGraph.builder()
                .goalInterpreter(interpreter)
                .seeders(seeders)
                .executor(executor)
                .actionRegistry(registry)
                .blackboardFactory(blackboardFactory)
                .build(), interpreter);
    }

    @Override
    public RuntimeInterpretationResponse interpret(String input) {
        return interpret(input, Map.of());
    }

    @Override
    public RuntimeInterpretationResponse interpret(String input, @Nullable Map<String, String> knownParameters) {
        return RuntimeInterpretationResponse.from(interpretGoal(input, knownParameters));
    }

    @Override
    public RuntimeStartResponse start(String input) {
        return start(input, Map.of());
    }

    @Override
    public RuntimeStartResponse start(String input, @Nullable Map<String, String> knownParameters) {
        return start(input, knownParameters, Map.of());
    }

    @Override
    public RuntimeStartResponse start(
            String input,
            @Nullable Map<String, String> knownParameters,
            @Nullable Map<String, String> runMetadata
    ) {
        return start(input, knownParameters, runMetadata, RunPrincipal.anonymous());
    }

    @Override
    public RuntimeStartResponse start(
            String input,
            @Nullable Map<String, String> knownParameters,
            @Nullable Map<String, String> runMetadata,
            RunPrincipal principal
    ) {
        return start(interpretGoal(input, knownParameters), runMetadata, principal);
    }

    @Override
    public RuntimeStartResponse start(GoalInterpretation interpretation) {
        return start(interpretation, Map.of());
    }

    @Override
    public RuntimeStartResponse start(GoalInterpretation interpretation, @Nullable Map<String, String> runMetadata) {
        return start(interpretation, runMetadata, RunPrincipal.anonymous());
    }

    @Override
    public RuntimeStartResponse start(
            GoalInterpretation interpretation,
            @Nullable Map<String, String> runMetadata,
            RunPrincipal principal
    ) {
        Objects.requireNonNull(interpretation, "interpretation");
        RuntimeInterpretationResponse interpretationResponse = RuntimeInterpretationResponse.from(interpretation);
        if (!interpretation.isReady()) {
            return RuntimeStartResponse.clarificationRequired(interpretationResponse);
        }

        RunResult result = actionGraph.start(interpretation, safeStringMap(runMetadata, "run metadata key"),
                principal);
        attachSampleRunId(result.runId());
        return RuntimeStartResponse.runStarted(interpretationResponse, RuntimeRunResponse.from(result));
    }

    @Override
    public RuntimeRunResponse resume(String runId) {
        return resume(runId, Map.of());
    }

    @Override
    public RuntimeRunResponse resume(String runId, @Nullable Map<String, String> runMetadata) {
        return resume(runId, runMetadata, RunPrincipal.anonymous());
    }

    @Override
    public RuntimeRunResponse resume(String runId, @Nullable Map<String, String> runMetadata, RunPrincipal actedBy) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        return RuntimeRunResponse.from(actionGraph.resume(runId, safeStringMap(runMetadata, "run metadata key"),
                actedBy));
    }

    private GoalInterpretation interpretGoal(String input, @Nullable Map<String, String> knownParameters) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        Map<String, String> safeKnownParameters = safeStringMap(knownParameters, "known parameter key");
        if (safeKnownParameters.isEmpty()) {
            return interpreter.interpret(input);
        }
        return interpreter.interpret(input, com.actiongraph.interpretation.GoalParameters.of(safeKnownParameters));
    }

    private void attachSampleRunId(String runId) {
        if (interpreter instanceof InterpretationSampleTracker tracker) {
            tracker.lastSampleId().ifPresent(sampleId -> tracker.attachRunId(sampleId, runId));
        }
    }

    private static Map<String, String> safeStringMap(@Nullable Map<String, String> values, String keyName) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        values.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(keyName + " must not be blank");
            }
        });
        return values.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() == null ? "" : entry.getValue()
                ));
    }
}
