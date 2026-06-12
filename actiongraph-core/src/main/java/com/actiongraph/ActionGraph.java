package com.actiongraph;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.annotation.AnnotatedActionFactory;
import com.actiongraph.api.Experimental;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.exception.ActionGraphInputException;
import com.actiongraph.fingerprint.ActionGraphFingerprints;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.interpretation.ClarificationQuestion;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameterDefinition;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.MissingField;
import com.actiongraph.interpretation.annotation.AnnotatedGoalFactory;
import com.actiongraph.interpretation.annotation.AnnotatedGoalSeederFactory;
import com.actiongraph.interpretation.annotation.GoalValueConverterResolver;
import com.actiongraph.interpretation.annotation.TypedGoalValueConverter;
import com.actiongraph.interpretation.sampling.InterpretationSampleTracker;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunResult;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Golden-path facade for starting, chatting into, and resuming ActionGraph runs.
 */
@Experimental(
        since = "0.2.0",
        value = "The root ActionGraph facade is experimental until DX2 pilots validate the golden path."
)
public final class ActionGraph {
    private final com.actiongraph.interpretation.GoalCatalog catalog;
    private final GoalBlackboardSeederRegistry seeders;
    private final GoapExecutor executor;
    private final ActionRegistry registry;
    private final @Nullable GoalInterpreter interpreter;
    private final Supplier<? extends Blackboard> blackboardFactory;
    private final String actionGraphFingerprint;
    private final Map<GoalType, String> goalFingerprints;

    private ActionGraph(Builder builder) {
        this.catalog = Objects.requireNonNull(builder.catalog, "catalog");
        this.seeders = Objects.requireNonNull(builder.seeders, "seeders");
        this.executor = builder.executor == null ? new GoapExecutor() : builder.executor;
        this.registry = Objects.requireNonNull(builder.registry, "registry");
        this.interpreter = builder.interpreter;
        this.blackboardFactory = Objects.requireNonNull(builder.blackboardFactory, "blackboardFactory");
        this.actionGraphFingerprint = ActionGraphFingerprints.actionGraph(this.registry.all());
        this.goalFingerprints = this.catalog.all().stream()
                .collect(Collectors.toUnmodifiableMap(
                        GoalDefinition::type,
                        ActionGraphFingerprints::goal
                ));
        this.seeders.registerDefaultSeeders(
                this.catalog,
                builder.effectiveConverterResolver(),
                builder.autoSeedingEnabled
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public RunResult start(String goalType, Map<String, String> parameters) {
        return start(goalType, parameters, Map.of());
    }

    @Experimental(
            since = "0.2.0",
            value = "Run principal propagation is experimental until STD1 identity pilots settle."
    )
    public RunResult start(String goalType, Map<String, String> parameters, RunPrincipal principal) {
        return start(goalType, parameters, Map.of(), principal);
    }

    public RunResult start(
            String goalType,
            @Nullable Map<String, String> parameters,
            @Nullable Map<String, String> runMetadata
    ) {
        return start(goalType, parameters, runMetadata, RunPrincipal.anonymous());
    }

    @Experimental(
            since = "0.2.0",
            value = "Run principal propagation is experimental until STD1 identity pilots settle."
    )
    public RunResult start(
            String goalType,
            @Nullable Map<String, String> parameters,
            @Nullable Map<String, String> runMetadata,
            RunPrincipal principal
    ) {
        GoalDefinition definition = definition(goalType);
        GoalParameters goalParameters = GoalParameters.of(safeStringMap(parameters, "goal parameter name"));
        assertRequiredParameters(definition, goalParameters);
        return start(GoalInterpretation.ready(definition.type(), goalParameters, definition.goal()),
                runMetadata, principal);
    }

    public ChatResult chat(String input) {
        return chat(input, Map.of());
    }

    @Experimental(
            since = "0.2.0",
            value = "Run principal propagation is experimental until STD1 identity pilots settle."
    )
    public ChatResult chat(String input, RunPrincipal principal) {
        return chat(input, Map.of(), Map.of(), principal);
    }

    public ChatResult chat(String input, @Nullable Map<String, String> knownParameters) {
        return chat(input, knownParameters, Map.of());
    }

    @Experimental(
            since = "0.2.0",
            value = "Run principal propagation is experimental until STD1 identity pilots settle."
    )
    public ChatResult chat(
            String input,
            @Nullable Map<String, String> knownParameters,
            RunPrincipal principal
    ) {
        return chat(input, knownParameters, Map.of(), principal);
    }

    public ChatResult chat(
            String input,
            @Nullable Map<String, String> knownParameters,
            @Nullable Map<String, String> runMetadata
    ) {
        return chat(input, knownParameters, runMetadata, RunPrincipal.anonymous());
    }

    @Experimental(
            since = "0.2.0",
            value = "Run principal propagation is experimental until STD1 identity pilots settle."
    )
    public ChatResult chat(
            String input,
            @Nullable Map<String, String> knownParameters,
            @Nullable Map<String, String> runMetadata,
            RunPrincipal principal
    ) {
        GoalInterpretation interpretation = interpret(input, knownParameters);
        GoalInterpretation checked = withMissingRequiredParameters(interpretation);
        if (!checked.isReady()) {
            return new ChatResult(checked, null);
        }
        RunResult result = start(checked, runMetadata, principal);
        attachSampleRunId(result.runId());
        return new ChatResult(checked, result);
    }

    public RunResult resume(String runId) {
        return resume(runId, Map.of());
    }

    @Experimental(
            since = "0.2.0",
            value = "Run resume actor propagation is experimental until STD1 identity pilots settle."
    )
    public RunResult resume(String runId, RunPrincipal actedBy) {
        return resume(runId, Map.of(), actedBy);
    }

    public RunResult resume(String runId, @Nullable Map<String, String> runMetadata) {
        return resume(runId, runMetadata, RunPrincipal.anonymous());
    }

    @Experimental(
            since = "0.2.0",
            value = "Run resume actor propagation is experimental until STD1 identity pilots settle."
    )
    public RunResult resume(String runId, @Nullable Map<String, String> runMetadata, RunPrincipal actedBy) {
        if (runId == null || runId.isBlank()) {
            throw new ActionGraphInputException("runId must not be blank");
        }
        return executor.resume(runId, registry.all(), registry, safeStringMap(runMetadata, "run metadata key"),
                actedBy);
    }

    private GoalInterpretation interpret(String input) {
        return interpret(input, Map.of());
    }

    private GoalInterpretation interpret(String input, @Nullable Map<String, String> knownParameters) {
        if (input == null || input.isBlank()) {
            throw new ActionGraphInputException("input must not be blank");
        }
        GoalInterpreter goalInterpreter = interpreter;
        if (goalInterpreter == null) {
            throw new ActionGraphConfigurationException("No GoalInterpreter configured for ActionGraph.chat");
        }
        GoalParameters safeKnownParameters =
                GoalParameters.of(safeStringMap(knownParameters, "known parameter name"));
        if (safeKnownParameters.values().isEmpty()) {
            return goalInterpreter.interpret(input);
        }
        return goalInterpreter.interpret(input, safeKnownParameters);
    }

    private void attachSampleRunId(String runId) {
        if (interpreter instanceof InterpretationSampleTracker tracker) {
            tracker.lastSampleId().ifPresent(sampleId -> tracker.attachRunId(sampleId, runId));
        }
    }

    public RunResult start(GoalInterpretation interpretation, @Nullable Map<String, String> runMetadata) {
        return start(interpretation, runMetadata, RunPrincipal.anonymous());
    }

    @Experimental(
            since = "0.2.0",
            value = "Run principal propagation is experimental until STD1 identity pilots settle."
    )
    public RunResult start(
            GoalInterpretation interpretation,
            @Nullable Map<String, String> runMetadata,
            RunPrincipal principal
    ) {
        Objects.requireNonNull(interpretation, "interpretation");
        if (!interpretation.isReady()) {
            throw new ActionGraphInputException("Cannot start run from an incomplete goal interpretation");
        }
        Blackboard blackboard = Objects.requireNonNull(
                blackboardFactory.get(),
                "blackboardFactory returned null"
        );
        seeders.seed(interpretation, blackboard);
        return executor.run(
                interpretation.goal().orElseThrow(),
                blackboard,
                registry.all(),
                registry,
                fingerprintMetadata(interpretation.goalType(), runMetadata),
                principal
        );
    }

    public String actionGraphFingerprint() {
        return actionGraphFingerprint;
    }

    public Optional<String> goalFingerprint(String goalType) {
        return catalog.byType(new GoalType(requireText(goalType, "goalType")))
                .flatMap(definition -> Optional.ofNullable(goalFingerprints.get(definition.type())));
    }

    private GoalDefinition definition(String rawGoalType) {
        GoalType type = new GoalType(requireText(rawGoalType, "goalType"));
        return catalog.byType(type)
                .orElseThrow(() -> new ActionGraphInputException(
                        "Unknown goalType: " + type.value() + ". Available goalTypes: " + availableGoalTypes()));
    }

    private String availableGoalTypes() {
        return catalog.all().stream()
                .map(definition -> definition.type().value())
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private void assertRequiredParameters(GoalDefinition definition, GoalParameters parameters) {
        Set<MissingField> missing = missingRequiredParameters(definition, parameters);
        if (!missing.isEmpty()) {
            throw new ActionGraphInputException("Missing required goal parameter(s): "
                    + missing.stream().map(MissingField::name).sorted().collect(Collectors.joining(", ")));
        }
    }

    private GoalInterpretation withMissingRequiredParameters(GoalInterpretation interpretation) {
        GoalDefinition definition = catalog.byType(interpretation.goalType()).orElse(null);
        if (definition == null) {
            return interpretation;
        }
        Set<MissingField> missing = missingRequiredParameters(definition, interpretation.parameters());
        if (missing.isEmpty()) {
            return interpretation;
        }
        return GoalInterpretation.needsClarification(
                interpretation.goalType(),
                interpretation.parameters(),
                missing,
                clarificationFor(missing)
        );
    }

    private Set<MissingField> missingRequiredParameters(GoalDefinition definition, GoalParameters parameters) {
        return definition.parameters().stream()
                .filter(GoalParameterDefinition::required)
                .filter(parameter -> parameters.get(parameter.name())
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .isEmpty())
                .map(parameter -> new MissingField(parameter.name()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private ClarificationQuestion clarificationFor(Set<MissingField> missing) {
        String names = missing.stream()
                .map(MissingField::name)
                .sorted()
                .collect(Collectors.joining(", "));
        return new ClarificationQuestion("Please provide " + names + ".");
    }

    private static Map<String, String> safeStringMap(@Nullable Map<String, String> values, String keyName) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> safe = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new ActionGraphInputException(keyName + " must not be blank");
            }
            safe.put(key, value == null ? "" : value);
        });
        return Map.copyOf(safe);
    }

    private Map<String, String> fingerprintMetadata(
            GoalType goalType,
            @Nullable Map<String, String> runMetadata
    ) {
        Map<String, String> merged = new LinkedHashMap<>(safeStringMap(runMetadata, "run metadata key"));
        merged.put("goalFingerprint", goalFingerprints.getOrDefault(goalType, ""));
        merged.put("actionGraphFingerprint", actionGraphFingerprint);
        return Map.copyOf(merged);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new ActionGraphInputException(name + " must not be blank");
        }
        return value.trim();
    }

    public static final class Builder {
        private com.actiongraph.interpretation.GoalCatalog catalog =
                new com.actiongraph.interpretation.GoalCatalog();
        private GoalBlackboardSeederRegistry seeders = new GoalBlackboardSeederRegistry();
        private @Nullable GoapExecutor executor;
        private ActionRegistry registry = new DefaultActionRegistry();
        private @Nullable GoalInterpreter interpreter;
        private Supplier<? extends Blackboard> blackboardFactory = InMemoryBlackboard::new;
        private GoalValueConverterResolver converterResolver = GoalValueConverterResolver.reflection();
        private final List<TypedGoalValueConverter<?>> typedConverters = new ArrayList<>();
        private boolean autoSeedingEnabled = true;

        private Builder() {
        }

        public Builder goalCatalog(com.actiongraph.interpretation.GoalCatalog catalog) {
            this.catalog = Objects.requireNonNull(catalog, "catalog");
            return this;
        }

        public Builder goal(GoalDefinition definition) {
            this.catalog.register(definition);
            return this;
        }

        public Builder goals(Collection<GoalDefinition> definitions) {
            Objects.requireNonNull(definitions, "definitions").forEach(this::goal);
            return this;
        }

        public Builder seeders(GoalBlackboardSeederRegistry seeders) {
            this.seeders = Objects.requireNonNull(seeders, "seeders");
            return this;
        }

        public Builder seeder(GoalBlackboardSeeder seeder) {
            this.seeders.register(seeder);
            return this;
        }

        public Builder actionRegistry(ActionRegistry registry) {
            this.registry = Objects.requireNonNull(registry, "registry");
            return this;
        }

        public Builder action(Action action) {
            this.registry.register(action);
            return this;
        }

        public Builder actions(Collection<Action> actions) {
            Objects.requireNonNull(actions, "actions").forEach(this::action);
            return this;
        }

        public Builder executor(GoapExecutor executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        public Builder goalInterpreter(GoalInterpreter interpreter) {
            this.interpreter = Objects.requireNonNull(interpreter, "interpreter");
            return this;
        }

        public Builder blackboardFactory(Supplier<? extends Blackboard> blackboardFactory) {
            this.blackboardFactory = Objects.requireNonNull(blackboardFactory, "blackboardFactory");
            return this;
        }

        public Builder goalValueConverterResolver(GoalValueConverterResolver converterResolver) {
            this.converterResolver = Objects.requireNonNull(converterResolver, "converterResolver");
            return this;
        }

        public Builder addConverter(TypedGoalValueConverter<?> converter) {
            this.typedConverters.add(Objects.requireNonNull(converter, "converter"));
            return this;
        }

        public Builder autoSeeding(boolean enabled) {
            this.autoSeedingEnabled = enabled;
            return this;
        }

        public Builder annotatedBeans(Object... targets) {
            List<GoalDefinition> definitions = AnnotatedGoalFactory.definitions(targets);
            for (GoalDefinition definition : definitions) {
                goal(definition);
            }
            for (Action action : AnnotatedActionFactory.actions(targets)) {
                action(action);
            }
            for (GoalBlackboardSeeder seeder : AnnotatedGoalSeederFactory.seeders(
                    effectiveConverterResolver(),
                    catalog.all(),
                    targets
            )) {
                seeder(seeder);
            }
            return this;
        }

        public ActionGraph build() {
            return new ActionGraph(this);
        }

        private GoalValueConverterResolver effectiveConverterResolver() {
            if (typedConverters.isEmpty()) {
                return converterResolver;
            }
            return new GoalValueConverterResolver() {
                @Override
                public com.actiongraph.interpretation.annotation.GoalValueConverter<?> resolve(
                        Class<? extends com.actiongraph.interpretation.annotation.GoalValueConverter<?>> converterType
                ) {
                    return converterResolver.resolve(converterType);
                }

                @Override
                public Optional<com.actiongraph.interpretation.annotation.GoalValueConverter<?>> resolveForType(
                        Class<?> targetType
                ) {
                    List<TypedGoalValueConverter<?>> matches = typedConverters.stream()
                            .filter(converter -> targetType.equals(converter.targetType()))
                            .toList();
                    if (matches.size() > 1) {
                        throw new ActionGraphConfigurationException(
                                "Multiple typed goal value converters registered for " + targetType.getName()
                                        + ": " + matches.stream()
                                        .map(converter -> converter.getClass().getName())
                                        .sorted()
                                        .collect(Collectors.joining(", ")));
                    }
                    if (matches.size() == 1) {
                        return Optional.of(matches.getFirst());
                    }
                    return converterResolver.resolveForType(targetType);
                }
            };
        }
    }
}
