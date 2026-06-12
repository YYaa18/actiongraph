package com.actiongraph.interpretation.annotation;

import com.actiongraph.action.annotation.BlackboardValue;
import com.actiongraph.api.Experimental;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.exception.ActionGraphInputException;
import com.actiongraph.exception.ActionGraphIntegrationException;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.BlackboardKey;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Builds {@link GoalBlackboardSeeder} instances from {@link ActionGraphGoalSeeder} methods.
 */
@Experimental(
        since = "0.2.0",
        value = "Annotated goal seeder factory is experimental until pilot domains validate binding conventions."
)
public final class AnnotatedGoalSeederFactory {
    private AnnotatedGoalSeederFactory() {
    }

    public static List<GoalBlackboardSeeder> seeders(Object... targets) {
        return seeders(GoalValueConverterResolver.reflection(), targets);
    }

    public static List<GoalBlackboardSeeder> seeders(
            Collection<GoalDefinition> goals,
            Object... targets
    ) {
        return seeders(GoalValueConverterResolver.reflection(), goals, targets);
    }

    public static List<GoalBlackboardSeeder> seeders(
            GoalValueConverterResolver converterResolver,
            Object... targets
    ) {
        return seeders(converterResolver, List.of(), targets);
    }

    public static List<GoalBlackboardSeeder> seeders(
            GoalValueConverterResolver converterResolver,
            Collection<GoalDefinition> goals,
            Object... targets
    ) {
        Objects.requireNonNull(converterResolver, "converterResolver");
        Map<GoalType, Set<Condition>> inheritedSeedConditions = inheritedSeedConditions(goals);
        Map<GoalType, GoalBlackboardSeeder> seeders = new LinkedHashMap<>();
        if (targets == null) {
            return List.of();
        }
        for (Object target : targets) {
            if (target == null) {
                continue;
            }
            Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
            Object invocationTarget = target instanceof Class<?> ? null : target;
            for (Method method : allMethods(type)) {
                ActionGraphGoalSeeder annotation = method.getAnnotation(ActionGraphGoalSeeder.class);
                if (annotation == null) {
                    continue;
                }
                method.setAccessible(true);
                validateMethodTarget(invocationTarget, method);
                GoalBlackboardSeeder seeder = new AnnotatedMethodGoalSeeder(
                        invocationTarget,
                        method,
                        annotation,
                        converterResolver,
                        inheritedSeedConditions
                );
                putUnique(seeders, seeder);
            }
        }
        return seeders.values().stream()
                .sorted(Comparator.comparing(seeder -> seeder.goalType().value()))
                .toList();
    }

    private static void validateMethodTarget(Object target, Method method) {
        if (target == null && !Modifier.isStatic(method.getModifiers())) {
            throw new ActionGraphConfigurationException(
                    "Annotated goal seeder method " + method + " is not static; pass a target instance");
        }
        Class<?> returnType = method.getReturnType();
        if (returnType.equals(Void.TYPE) || returnType.equals(SeedResult.class)) {
            return;
        }
        boolean writesByDeclaredKey = method.getAnnotation(BlackboardValue.class) != null;
        if (!writesByDeclaredKey && (returnType.isInterface() || Modifier.isAbstract(returnType.getModifiers()))) {
            throw new ActionGraphConfigurationException(
                    "Annotated goal seeder method " + method
                            + " must return a concrete type, void, SeedResult, or use @BlackboardValue");
        }
    }

    private static void putUnique(Map<GoalType, GoalBlackboardSeeder> seeders, GoalBlackboardSeeder seeder) {
        GoalBlackboardSeeder previous = seeders.putIfAbsent(seeder.goalType(), seeder);
        if (previous != null) {
            throw new ActionGraphConfigurationException(
                    "Duplicate annotated goal seeder for goal type: " + seeder.goalType().value());
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

    private static Map<GoalType, Set<Condition>> inheritedSeedConditions(Collection<GoalDefinition> goals) {
        if (goals == null || goals.isEmpty()) {
            return Map.of();
        }
        Map<GoalType, Set<Condition>> conditions = new LinkedHashMap<>();
        for (GoalDefinition goal : goals) {
            if (goal != null && !goal.seedConditions().isEmpty()) {
                conditions.put(goal.type(), goal.seedConditions());
            }
        }
        return Map.copyOf(conditions);
    }

    private static final class AnnotatedMethodGoalSeeder implements GoalBlackboardSeeder {
        private final Object target;
        private final Method method;
        private final ActionGraphGoalSeeder metadata;
        private final GoalParameterBinder binder;
        private final GoalType goalType;
        private final Set<Condition> seedConditions;

        private AnnotatedMethodGoalSeeder(
                Object target,
                Method method,
                ActionGraphGoalSeeder metadata,
                GoalValueConverterResolver converterResolver,
                Map<GoalType, Set<Condition>> inheritedSeedConditions
        ) {
            this.target = target;
            this.method = method;
            this.metadata = metadata;
            this.binder = new GoalParameterBinder(converterResolver);
            this.goalType = new GoalType(resolveGoalType(metadata));
            Set<Condition> declared = conditions(metadata.seedConditions());
            this.seedConditions = declared.isEmpty()
                    ? inheritedSeedConditions.getOrDefault(goalType, Set.of())
                    : declared;
            validateBindingParameters();
        }

        @Override
        public GoalType goalType() {
            return goalType;
        }

        @Override
        public Optional<Set<Condition>> declaredSeedConditions() {
            return Optional.of(seedConditions);
        }

        @Override
        public void seed(GoalParameters parameters, Blackboard blackboard) {
            Object[] args = resolveArguments(parameters, blackboard);
            Object result = invoke(args);
            applyResult(result, blackboard);
            seedConditions.forEach(blackboard::addCondition);
        }

        private void validateBindingParameters() {
            for (Parameter parameter : method.getParameters()) {
                if (parameter.getAnnotation(BindGoalParams.class) != null) {
                    binder.validateRecordSchema(parameter.getType());
                }
            }
        }

        private Object[] resolveArguments(GoalParameters parameters, Blackboard blackboard) {
            Parameter[] methodParameters = method.getParameters();
            Object[] args = new Object[methodParameters.length];
            for (int i = 0; i < methodParameters.length; i++) {
                args[i] = resolveArgument(methodParameters[i], parameters, blackboard);
            }
            return args;
        }

        private Object resolveArgument(Parameter parameter, GoalParameters parameters, Blackboard blackboard) {
            Class<?> parameterType = parameter.getType();
            if (parameterType.equals(GoalParameters.class)) {
                return parameters;
            }
            if (parameterType.equals(Blackboard.class)) {
                return blackboard;
            }
            BindGoalParams bindGoalParams = parameter.getAnnotation(BindGoalParams.class);
            if (bindGoalParams != null) {
                if (parameter.getAnnotation(FromGoalParam.class) != null
                        || parameter.getAnnotation(BlackboardValue.class) != null) {
                    throw new ActionGraphConfigurationException(
                            "@BindGoalParams cannot be combined with @FromGoalParam or @BlackboardValue on "
                                    + parameter);
                }
                return binder.bindRecord(parameterType, parameters, blackboard, bindGoalParams.atLeastOne());
            }
            BlackboardValue blackboardValue = parameter.getAnnotation(BlackboardValue.class);
            if (blackboardValue != null && parameter.getAnnotation(FromGoalParam.class) == null) {
                return blackboard.get(BlackboardKey.of(parameterType, blackboardValue.value()))
                        .orElseThrow(() -> new ActionGraphInputException(
                                "Missing blackboard value for " + parameterType.getName()
                                        + "#" + blackboardValue.value()));
            }
            return binder.bindParameter(parameter, parameters, blackboard);
        }

        private Object invoke(Object[] args) {
            try {
                return method.invoke(target, args);
            } catch (IllegalAccessException ex) {
                throw new ActionGraphConfigurationException("Cannot access annotated goal seeder method", ex);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new ActionGraphIntegrationException("Annotated goal seeder method failed", cause);
            }
        }

        private void applyResult(Object result, Blackboard blackboard) {
            if (method.getReturnType().equals(Void.TYPE)) {
                return;
            }
            if (result == null) {
                throw new ActionGraphInputException("Annotated goal seeder returned null for " + goalType.value());
            }
            if (result instanceof SeedResult seedResult) {
                applySeedResult(seedResult, blackboard);
                return;
            }
            BlackboardValue key = method.getAnnotation(BlackboardValue.class);
            if (key == null) {
                blackboard.put(result);
                return;
            }
            writeByKey(blackboard, method.getReturnType(), key.value(), result);
        }

        private void applySeedResult(SeedResult seedResult, Blackboard blackboard) {
            for (SeedResult.Value value : seedResult.values()) {
                writeValue(blackboard, value.key(), value.value());
            }
            seedResult.conditions().forEach(blackboard::addCondition);
        }

        private <T> void writeByKey(Blackboard blackboard, Class<T> type, String id, Object value) {
            blackboard.put(BlackboardKey.of(type, id), type.cast(value));
        }

        private <T> void writeValue(Blackboard blackboard, BlackboardKey<T> key, Object value) {
            blackboard.put(key, key.type().cast(value));
        }
    }

    private static String resolveGoalType(ActionGraphGoalSeeder annotation) {
        String value = annotation.value();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Annotated goal seeder goal type must not be blank");
        }
        return value.trim();
    }

    private static Set<Condition> conditions(String[] keys) {
        Set<Condition> conditions = new LinkedHashSet<>();
        if (keys == null) {
            return Set.of();
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                throw new ActionGraphConfigurationException("seedConditions must not contain blank condition keys");
            }
            conditions.add(Condition.of(key));
        }
        return Set.copyOf(conditions);
    }

}
