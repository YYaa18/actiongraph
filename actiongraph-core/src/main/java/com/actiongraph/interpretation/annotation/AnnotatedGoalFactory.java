package com.actiongraph.interpretation.annotation;

import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalParameterDefinition;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds GoalDefinition instances from {@link ActionGraphGoal} annotations.
 */
public final class AnnotatedGoalFactory {
    private AnnotatedGoalFactory() {
    }

    /**
     * Derives goal catalog definitions from annotated classes or instances.
     *
     * <p>Passing a {@link Class} is enough because Goal metadata is declarative and
     * does not invoke target methods.
     *
     * @param targets annotated classes or instances; null values are ignored
     * @return deterministic list of inferred goal definitions sorted by goal type
     * @throws ActionGraphConfigurationException when metadata is ambiguous or duplicated
     */
    public static List<GoalDefinition> definitions(Object... targets) {
        Map<GoalType, GoalDefinition> definitions = new LinkedHashMap<>();
        if (targets == null) {
            return List.of();
        }
        for (Object target : targets) {
            if (target == null) {
                continue;
            }
            Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
            ActionGraphGoal typeAnnotation = type.getAnnotation(ActionGraphGoal.class);
            if (typeAnnotation != null) {
                putUnique(definitions, definitionFromType(type, typeAnnotation));
            }
            for (Method method : allMethods(type)) {
                ActionGraphGoal methodAnnotation = method.getAnnotation(ActionGraphGoal.class);
                if (methodAnnotation != null) {
                    putUnique(definitions, definitionFromMethod(method, methodAnnotation));
                }
            }
        }
        return definitions.values().stream()
                .sorted(Comparator.comparing(definition -> definition.type().value()))
                .toList();
    }

    private static GoalDefinition definitionFromType(Class<?> type, ActionGraphGoal annotation) {
        return definition(annotation, parameterDefinitionsFromSchema(type));
    }

    private static GoalDefinition definitionFromMethod(Method method, ActionGraphGoal annotation) {
        Class<?> schema = annotation.schema();
        if (schema != Void.class) {
            return definition(annotation, parameterDefinitionsFromSchema(schema));
        }
        return definition(annotation, parameterDefinitionsFromMethod(method));
    }

    private static GoalDefinition definition(ActionGraphGoal annotation, List<GoalParameterDefinition> parameters) {
        GoalType type = new GoalType(requireNonBlank(annotation.type(), "goal type"));
        String goalName = annotation.name().isBlank() ? type.value() : annotation.name().trim();
        Set<Condition> targetConditions = conditions(annotation.targetConditions(), "targetConditions");
        if (targetConditions.isEmpty()) {
            throw new ActionGraphConfigurationException(
                    "Annotated goal " + type.value() + " must declare at least one target condition");
        }
        return new GoalDefinition(
                type,
                annotation.description(),
                new Goal(goalName, targetConditions),
                parameters,
                conditions(annotation.seedConditions(), "seedConditions")
        );
    }

    private static List<GoalParameterDefinition> parameterDefinitionsFromMethod(Method method) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 1
                && parameters[0].getAnnotation(GoalParameter.class) == null
                && parameters[0].getType().isRecord()) {
            return parameterDefinitionsFromSchema(parameters[0].getType());
        }
        List<GoalParameterDefinition> definitions = new ArrayList<>();
        for (Parameter parameter : parameters) {
            definitions.add(parameterDefinition(
                    parameter.getAnnotation(GoalParameter.class),
                    inferredParameterName(parameter)
            ));
        }
        return List.copyOf(definitions);
    }

    private static List<GoalParameterDefinition> parameterDefinitionsFromSchema(Class<?> schema) {
        if (schema == Void.class) {
            return List.of();
        }
        if (schema.isRecord()) {
            List<GoalParameterDefinition> definitions = new ArrayList<>();
            for (RecordComponent component : schema.getRecordComponents()) {
                definitions.add(parameterDefinition(
                        component.getAnnotation(GoalParameter.class),
                        component.getName()
                ));
            }
            return List.copyOf(definitions);
        }
        List<Field> fields = Arrays.stream(schema.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .sorted(Comparator.comparing(Field::getName))
                .toList();
        List<GoalParameterDefinition> definitions = new ArrayList<>();
        for (Field field : fields) {
            definitions.add(parameterDefinition(field.getAnnotation(GoalParameter.class), field.getName()));
        }
        return List.copyOf(definitions);
    }

    private static GoalParameterDefinition parameterDefinition(GoalParameter annotation, String inferredName) {
        String name = inferredName;
        String description = humanize(inferredName);
        boolean required = true;
        Optional<String> example = Optional.empty();
        if (annotation != null) {
            name = firstNonBlank(annotation.name(), annotation.value(), inferredName);
            description = annotation.description().isBlank() ? humanize(name) : annotation.description();
            required = annotation.required();
            example = annotation.example().isBlank() ? Optional.empty() : Optional.of(annotation.example());
        }
        return new GoalParameterDefinition(name, description, required, example);
    }

    private static String inferredParameterName(Parameter parameter) {
        GoalParameter annotation = parameter.getAnnotation(GoalParameter.class);
        if (annotation != null) {
            String annotatedName = firstNonBlank(annotation.name(), annotation.value(), "");
            if (!annotatedName.isBlank()) {
                return annotatedName;
            }
        }
        if (parameter.isNamePresent()) {
            return parameter.getName();
        }
        throw new ActionGraphConfigurationException(
                "Cannot infer goal parameter name for " + parameter.getDeclaringExecutable()
                        + ". Compile with -parameters, use @GoalParameter(name=...), or pass a record schema.");
    }

    private static void putUnique(Map<GoalType, GoalDefinition> definitions, GoalDefinition definition) {
        GoalDefinition previous = definitions.putIfAbsent(definition.type(), definition);
        if (previous != null) {
            throw new ActionGraphConfigurationException("Duplicate annotated goal type: " + definition.type().value());
        }
    }

    private static List<Method> allMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            methods.addAll(Arrays.asList(current.getDeclaredMethods()));
            current = current.getSuperclass();
        }
        return methods;
    }

    private static Set<Condition> conditions(String[] keys, String fieldName) {
        Set<Condition> conditions = new LinkedHashSet<>();
        if (keys == null) {
            return Set.of();
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                throw new ActionGraphConfigurationException(fieldName + " must not contain blank condition keys");
            }
            conditions.add(Condition.of(key));
        }
        return Set.copyOf(conditions);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return fallback;
    }

    private static String humanize(String name) {
        String spaced = name.replace('_', ' ').replace('-', ' ');
        spaced = spaced.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        return spaced.isBlank() ? "" : spaced.substring(0, 1).toUpperCase() + spaced.substring(1);
    }
}
