package com.actiongraph.interpretation.config;

import com.actiongraph.api.Experimental;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalParameterDefinition;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.annotation.GoalValueConverterResolver;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Converts external goal declarations into runtime {@link GoalDefinition}s.
 */
@Experimental(
        since = "0.2.0",
        value = "External goal configuration is experimental until DX4 pilots validate bundle conventions."
)
public final class ConfiguredGoalDefinitionFactory {
    private static final Map<String, Class<?>> BUILT_IN_TYPES = builtInTypes();

    private final ClassLoader classLoader;
    private final GoalValueConverterResolver converterResolver;

    public ConfiguredGoalDefinitionFactory(GoalValueConverterResolver converterResolver) {
        this(Thread.currentThread().getContextClassLoader(), converterResolver);
    }

    public ConfiguredGoalDefinitionFactory(ClassLoader classLoader, GoalValueConverterResolver converterResolver) {
        this.classLoader = classLoader == null ? ConfiguredGoalDefinitionFactory.class.getClassLoader() : classLoader;
        this.converterResolver = Objects.requireNonNull(converterResolver, "converterResolver");
    }

    public List<GoalDefinition> definitions(Collection<ConfiguredGoalDefinition> declarations) {
        Objects.requireNonNull(declarations, "declarations");
        return declarations.stream()
                .filter(ConfiguredGoalDefinition::enabled)
                .map(this::definition)
                .toList();
    }

    public GoalDefinition definition(ConfiguredGoalDefinition declaration) {
        Objects.requireNonNull(declaration, "declaration");
        Set<Condition> targets = conditions(declaration.targetConditions(), declaration.source(), "target-conditions");
        if (targets.isEmpty()) {
            throw new ActionGraphConfigurationException(
                    declaration.source() + " must declare at least one target-condition");
        }
        Set<Condition> seeds = conditions(declaration.seedConditions(), declaration.source(), "seed-conditions");
        List<GoalParameterDefinition> parameters = declaration.parameters().stream()
                .map(parameter -> parameter(parameter, declaration.source()))
                .toList();
        return new GoalDefinition(
                new GoalType(declaration.type().trim()),
                declaration.description(),
                new Goal(declaration.type().trim(), targets),
                parameters,
                Void.class,
                seeds,
                true
        );
    }

    public Class<?> resolveType(String rawType, String source) {
        String type = rawType == null || rawType.isBlank() ? "string" : rawType.trim();
        Class<?> builtIn = BUILT_IN_TYPES.get(type.toLowerCase(Locale.ROOT));
        if (builtIn != null) {
            return builtIn;
        }
        Class<?> resolved;
        try {
            resolved = Class.forName(type, false, classLoader);
        } catch (ClassNotFoundException ex) {
            throw new ActionGraphConfigurationException(source + " references unknown parameter type: " + type, ex);
        }
        if (isBuiltInType(resolved)) {
            return resolved;
        }
        Optional<?> converter = converterResolver.resolveForType(resolved);
        if (converter.isEmpty()) {
            throw new ActionGraphConfigurationException(source + " parameter type " + resolved.getName()
                    + " requires a TypedGoalValueConverter bean");
        }
        return resolved;
    }

    private GoalParameterDefinition parameter(ConfiguredGoalParameter parameter, String source) {
        Class<?> type = resolveType(parameter.type(), source + " parameter '" + parameter.name() + "'");
        return new GoalParameterDefinition(
                parameter.name().trim(),
                parameter.description(),
                parameter.required(),
                parameter.example().isBlank() ? Optional.empty() : Optional.of(parameter.example()),
                type
        );
    }

    private Set<Condition> conditions(List<String> values, String source, String propertyName) {
        Set<Condition> conditions = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new ActionGraphConfigurationException(source + " has blank " + propertyName + " entry");
            }
            conditions.add(Condition.of(value.trim()));
        }
        return Set.copyOf(conditions);
    }

    private boolean isBuiltInType(Class<?> type) {
        return BUILT_IN_TYPES.containsValue(type);
    }

    private static Map<String, Class<?>> builtInTypes() {
        Map<String, Class<?>> types = new LinkedHashMap<>();
        types.put("string", String.class);
        types.put("str", String.class);
        types.put("int", Integer.class);
        types.put("integer", Integer.class);
        types.put("long", Long.class);
        types.put("decimal", BigDecimal.class);
        types.put("bigdecimal", BigDecimal.class);
        types.put("double", Double.class);
        types.put("boolean", Boolean.class);
        types.put("bool", Boolean.class);
        return Map.copyOf(types);
    }
}
