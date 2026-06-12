package com.actiongraph.interpretation.annotation;

import com.actiongraph.action.annotation.BlackboardValue;
import com.actiongraph.api.Experimental;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.exception.ActionGraphInputException;
import com.actiongraph.exception.ActionGraphIntegrationException;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
            GoalValueConverterResolver converterResolver,
            Object... targets
    ) {
        Objects.requireNonNull(converterResolver, "converterResolver");
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
                        converterResolver
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

    private static final class AnnotatedMethodGoalSeeder implements GoalBlackboardSeeder {
        private final Object target;
        private final Method method;
        private final ActionGraphGoalSeeder metadata;
        private final GoalValueConverterResolver converterResolver;
        private final GoalType goalType;
        private final Set<Condition> seedConditions;

        private AnnotatedMethodGoalSeeder(
                Object target,
                Method method,
                ActionGraphGoalSeeder metadata,
                GoalValueConverterResolver converterResolver
        ) {
            this.target = target;
            this.method = method;
            this.metadata = metadata;
            this.converterResolver = converterResolver;
            this.goalType = new GoalType(resolveGoalType(metadata));
            this.seedConditions = conditions(metadata.seedConditions());
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
            BlackboardValue blackboardValue = parameter.getAnnotation(BlackboardValue.class);
            if (blackboardValue != null && parameter.getAnnotation(FromGoalParam.class) == null) {
                return blackboard.get(BlackboardKey.of(parameterType, blackboardValue.value()))
                        .orElseThrow(() -> new ActionGraphInputException(
                                "Missing blackboard value for " + parameterType.getName()
                                        + "#" + blackboardValue.value()));
            }
            FromGoalParam goalParam = parameter.getAnnotation(FromGoalParam.class);
            String name = goalParamName(parameter, goalParam);
            boolean required = goalParam == null || goalParam.required();
            Optional<String> raw = parameters.get(name)
                    .map(String::trim)
                    .filter(value -> !value.isBlank());
            if (raw.isEmpty()) {
                if (required || parameterType.isPrimitive()) {
                    throw new ActionGraphInputException("Missing goal parameter: " + name);
                }
                return null;
            }
            GoalParameterBindingContext context =
                    new GoalParameterBindingContext(name, parameterType, parameters, blackboard);
            return convert(raw.get(), parameterType, goalParam, context);
        }

        private String goalParamName(Parameter parameter, FromGoalParam annotation) {
            if (annotation != null) {
                String name = firstNonBlank(annotation.name(), annotation.value(), "");
                if (!name.isBlank()) {
                    return name;
                }
            }
            if (parameter.isNamePresent()) {
                return parameter.getName();
            }
            throw new ActionGraphConfigurationException(
                    "Cannot infer goal parameter name for " + parameter.getDeclaringExecutable()
                            + ". Compile with -parameters or use @FromGoalParam(name=...).");
        }

        private Object convert(
                String raw,
                Class<?> parameterType,
                FromGoalParam annotation,
                GoalParameterBindingContext context
        ) {
            Class<? extends GoalValueConverter<?>> converterType =
                    annotation == null ? GoalValueConverter.None.class : annotation.converter();
            if (!converterType.equals(GoalValueConverter.None.class)) {
                Object converted = converterResolver.resolve(converterType).convert(raw, context);
                return castConverted(parameterType, converted, context.parameterName());
            }
            return builtInConvert(raw, parameterType, context.parameterName());
        }

        private Object builtInConvert(String raw, Class<?> targetType, String parameterName) {
            Class<?> type = wrapperType(targetType);
            try {
                if (type.equals(String.class)) {
                    return raw;
                }
                if (type.equals(Integer.class)) {
                    return Integer.valueOf(raw);
                }
                if (type.equals(Long.class)) {
                    return Long.valueOf(raw);
                }
                if (type.equals(BigDecimal.class)) {
                    return new BigDecimal(raw);
                }
                if (type.equals(Double.class)) {
                    return Double.valueOf(raw);
                }
                if (type.equals(Boolean.class)) {
                    return parseBoolean(raw);
                }
                if (type.isEnum()) {
                    return parseEnum(raw, type);
                }
            } catch (IllegalArgumentException ex) {
                throw new ActionGraphInputException(
                        "Cannot convert goal parameter " + parameterName + " value '" + raw
                                + "' to " + type.getSimpleName(),
                        ex
                );
            }
            throw new ActionGraphConfigurationException(
                    "No built-in goal parameter converter for " + targetType.getName()
                            + "; use @FromGoalParam(converter=...)");
        }

        private Object parseEnum(String raw, Class<?> type) {
            String value = raw.trim();
            Object[] constants = type.getEnumConstants();
            for (Object constant : constants) {
                if (((Enum<?>) constant).name().equals(value)) {
                    return constant;
                }
            }
            for (Object constant : constants) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(value)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("Unsupported enum value: " + raw);
        }

        private Boolean parseBoolean(String raw) {
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "true", "yes", "y", "1", "是", "对" -> true;
                case "false", "no", "n", "0", "否", "不" -> false;
                default -> throw new IllegalArgumentException("Unsupported boolean value: " + raw);
            };
        }

        private Object castConverted(Class<?> parameterType, Object converted, String parameterName) {
            if (converted == null) {
                if (parameterType.isPrimitive()) {
                    throw new ActionGraphInputException(
                            "Goal converter returned null for primitive parameter: " + parameterName);
                }
                return null;
            }
            Class<?> type = wrapperType(parameterType);
            if (!type.isInstance(converted)) {
                throw new ActionGraphConfigurationException(
                        "Goal converter returned " + converted.getClass().getName()
                                + " for parameter " + parameterName + ", expected " + type.getName());
            }
            return converted;
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

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return fallback;
    }

    private static Class<?> wrapperType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type.equals(Integer.TYPE)) {
            return Integer.class;
        }
        if (type.equals(Long.TYPE)) {
            return Long.class;
        }
        if (type.equals(Boolean.TYPE)) {
            return Boolean.class;
        }
        if (type.equals(Double.TYPE)) {
            return Double.class;
        }
        if (type.equals(Float.TYPE)) {
            return Float.class;
        }
        if (type.equals(Short.TYPE)) {
            return Short.class;
        }
        if (type.equals(Byte.TYPE)) {
            return Byte.class;
        }
        if (type.equals(Character.TYPE)) {
            return Character.class;
        }
        return type;
    }
}
