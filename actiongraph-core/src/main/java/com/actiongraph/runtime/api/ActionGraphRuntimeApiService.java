package com.actiongraph.runtime.api;

import com.actiongraph.action.ActionRegistry;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunResult;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class ActionGraphRuntimeApiService implements ActionGraphRuntimeOperations {
    private final GoalInterpreter interpreter;
    private final GoalBlackboardSeederRegistry seeders;
    private final GoapExecutor executor;
    private final ActionRegistry registry;
    private final Supplier<? extends Blackboard> blackboardFactory;

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
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter");
        this.seeders = Objects.requireNonNull(seeders, "seeders");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.blackboardFactory = Objects.requireNonNull(blackboardFactory, "blackboardFactory");
    }

    @Override
    public RuntimeInterpretationResponse interpret(String input) {
        return interpret(input, Map.of());
    }

    @Override
    public RuntimeInterpretationResponse interpret(String input, Map<String, String> knownParameters) {
        return RuntimeInterpretationResponse.from(interpretGoal(input, knownParameters));
    }

    @Override
    public RuntimeStartResponse start(String input) {
        return start(input, Map.of());
    }

    @Override
    public RuntimeStartResponse start(String input, Map<String, String> knownParameters) {
        return start(input, knownParameters, Map.of());
    }

    @Override
    public RuntimeStartResponse start(
            String input,
            Map<String, String> knownParameters,
            Map<String, String> runMetadata
    ) {
        return start(interpretGoal(input, knownParameters), runMetadata);
    }

    @Override
    public RuntimeStartResponse start(GoalInterpretation interpretation) {
        return start(interpretation, Map.of());
    }

    @Override
    public RuntimeStartResponse start(GoalInterpretation interpretation, Map<String, String> runMetadata) {
        Objects.requireNonNull(interpretation, "interpretation");
        RuntimeInterpretationResponse interpretationResponse = RuntimeInterpretationResponse.from(interpretation);
        if (!interpretation.isReady()) {
            return RuntimeStartResponse.clarificationRequired(interpretationResponse);
        }

        Blackboard blackboard = Objects.requireNonNull(
                blackboardFactory.get(),
                "blackboardFactory returned null"
        );
        seeders.seed(interpretation, blackboard);
        RunResult result = executor.run(
                interpretation.goal().orElseThrow(),
                blackboard,
                registry.all(),
                registry,
                safeStringMap(runMetadata, "run metadata key")
        );
        return RuntimeStartResponse.runStarted(interpretationResponse, RuntimeRunResponse.from(result));
    }

    @Override
    public RuntimeRunResponse resume(String runId) {
        return resume(runId, Map.of());
    }

    @Override
    public RuntimeRunResponse resume(String runId, Map<String, String> runMetadata) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        return RuntimeRunResponse.from(executor.resume(runId, registry.all(), registry,
                safeStringMap(runMetadata, "run metadata key")));
    }

    private GoalInterpretation interpretGoal(String input, Map<String, String> knownParameters) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        Map<String, String> safeKnownParameters = knownParameters == null
                ? Map.of()
                : Map.copyOf(knownParameters);
        if (safeKnownParameters.isEmpty()) {
            return interpreter.interpret(input);
        }
        return interpreter.interpret(input, GoalParameters.of(safeKnownParameters));
    }

    private static Map<String, String> safeStringMap(Map<String, String> values, String keyName) {
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
