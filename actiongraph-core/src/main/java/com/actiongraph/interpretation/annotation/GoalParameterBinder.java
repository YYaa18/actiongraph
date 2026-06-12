package com.actiongraph.interpretation.annotation;

import com.actiongraph.api.Experimental;
import com.actiongraph.api.Internal;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.exception.ActionGraphInputException;
import com.actiongraph.exception.ActionGraphIntegrationException;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.runtime.Blackboard;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared binding pipeline for annotated seeders and schema-driven automatic seeders.
 */
@Internal
@Experimental(
        since = "0.2.0",
        value = "Shared goal parameter binding is internal plumbing while DX3 automatic seeding conventions settle."
)
public final class GoalParameterBinder {
    private final GoalValueConverterResolver converterResolver;

    public GoalParameterBinder(GoalValueConverterResolver converterResolver) {
        this.converterResolver = Objects.requireNonNull(converterResolver, "converterResolver");
    }

    public static GoalParameterBinder reflection() {
        return new GoalParameterBinder(GoalValueConverterResolver.reflection());
    }

    public Object bindParameter(Parameter parameter, GoalParameters parameters, Blackboard blackboard) {
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(blackboard, "blackboard");
        FromGoalParam goalParam = parameter.getAnnotation(FromGoalParam.class);
        String name = goalParamName(parameter, goalParam);
        boolean required = goalParam == null || goalParam.required();
        Optional<String> raw = raw(parameters, name);
        Class<?> parameterType = parameter.getType();
        if (raw.isEmpty()) {
            if (parameterType.equals(Optional.class)) {
                return Optional.empty();
            }
            if (required || parameterType.isPrimitive()) {
                throw new ActionGraphInputException("Missing goal parameter: " + name);
            }
            return null;
        }
        return convertGoalValue(
                raw.orElseThrow(),
                parameterType,
                optionalValueType(parameter.getParameterizedType()),
                goalParam == null ? GoalValueConverter.None.class : goalParam.converter(),
                new GoalParameterBindingContext(name, bindingContextType(parameterType, parameter.getParameterizedType()),
                        parameters, blackboard)
        );
    }

    public <T> T bindRecord(Class<T> targetType, GoalParameters parameters, Blackboard blackboard) {
        return bindRecord(targetType, parameters, blackboard, false);
    }

    public <T> T bindRecord(
            Class<T> targetType,
            GoalParameters parameters,
            Blackboard blackboard,
            boolean requireAtLeastOneOptionalValue
    ) {
        validateRecordSchema(targetType);
        RecordComponent[] components = targetType.getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] constructorTypes = new Class<?>[components.length];
        List<String> componentNames = new ArrayList<>();
        List<String> optionalParameterNames = new ArrayList<>();
        Map<String, String> componentParameterNames = new java.util.LinkedHashMap<>();
        boolean anyPresent = false;
        boolean anyOptionalPresent = false;
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            constructorTypes[i] = component.getType();
            GoalParameter metadata = component.getAnnotation(GoalParameter.class);
            String name = goalParamName(component, metadata);
            componentNames.add(component.getName());
            componentParameterNames.put(component.getName(), name);
            boolean optionalComponent = component.getType().equals(Optional.class);
            boolean required = !optionalComponent && isRequired(metadata);
            if (!required) {
                optionalParameterNames.add(name);
            }
            Optional<String> raw = raw(parameters, name);
            if (raw.isEmpty()) {
                if (optionalComponent) {
                    args[i] = Optional.empty();
                    continue;
                }
                if (required || component.getType().isPrimitive()) {
                    throw new ActionGraphInputException("Missing goal parameter: " + name);
                }
                args[i] = null;
                continue;
            }
            anyPresent = true;
            anyOptionalPresent = anyOptionalPresent || !required;
            args[i] = convertGoalValue(
                    raw.orElseThrow(),
                    component.getType(),
                    optionalValueType(component.getGenericType()),
                    metadata == null ? GoalValueConverter.None.class : metadata.converter(),
                    new GoalParameterBindingContext(
                            name,
                            bindingContextType(component.getType(), component.getGenericType()),
                            parameters,
                            blackboard
                    )
            );
        }
        assertAtLeastOne(targetType, parameters, componentNames, optionalParameterNames,
                componentParameterNames, anyPresent, anyOptionalPresent, requireAtLeastOneOptionalValue);
        return instantiateRecord(targetType, constructorTypes, args);
    }

    public void validateRecordSchema(Class<?> targetType) {
        Objects.requireNonNull(targetType, "targetType");
        if (!targetType.isRecord()) {
            throw new ActionGraphConfigurationException(
                    "Goal schema binding currently supports record types only: " + targetType.getName());
        }
        GoalSchema schema = targetType.getAnnotation(GoalSchema.class);
        if (schema == null || schema.atLeastOne().length == 0) {
            return;
        }
        Set<String> components = Arrays.stream(targetType.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> unknown = Arrays.stream(schema.atLeastOne())
                .filter(name -> name == null || name.isBlank() || !components.contains(name.trim()))
                .map(name -> name == null ? "<null>" : name)
                .toList();
        if (!unknown.isEmpty()) {
            throw new ActionGraphConfigurationException("@GoalSchema(atLeastOne) on " + targetType.getName()
                    + " references unknown component(s): " + String.join(", ", unknown)
                    + ". Available components: " + String.join(", ", components));
        }
    }

    private Optional<String> raw(GoalParameters parameters, String name) {
        return parameters.get(name)
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }

    private void assertAtLeastOne(
            Class<?> targetType,
            GoalParameters parameters,
            List<String> componentNames,
            List<String> optionalParameterNames,
            Map<String, String> componentParameterNames,
            boolean anyPresent,
            boolean anyOptionalPresent,
            boolean requireAtLeastOneOptionalValue
    ) {
        GoalSchema schema = targetType.getAnnotation(GoalSchema.class);
        if (schema != null && schema.atLeastOne().length > 0) {
            List<String> candidates = Arrays.stream(schema.atLeastOne())
                    .map(String::trim)
                    .toList();
            boolean anyCandidatePresent = candidates.stream()
                    .map(componentParameterNames::get)
                    .anyMatch(name -> raw(parameters, name).isPresent());
            if (!anyCandidatePresent) {
                throw new ActionGraphInputException("At least one goal parameter must be provided for "
                        + targetType.getSimpleName() + ": " + String.join(", ", candidates));
            }
            return;
        }
        if (!requireAtLeastOneOptionalValue) {
            return;
        }
        boolean hasOptionalParameters = !optionalParameterNames.isEmpty();
        if (!(hasOptionalParameters ? anyOptionalPresent : anyPresent)) {
            List<String> candidates = hasOptionalParameters ? optionalParameterNames : componentNames;
            throw new ActionGraphInputException("At least one goal parameter must be provided for "
                    + targetType.getSimpleName() + ": " + String.join(", ", candidates));
        }
    }

    private Object convertGoalValue(
            String raw,
            Class<?> targetType,
            Optional<Class<?>> optionalValueType,
            Class<? extends GoalValueConverter<?>> converterType,
            GoalParameterBindingContext context
    ) {
        if (targetType.equals(Optional.class)) {
            Class<?> valueType = optionalValueType.orElseThrow(() -> new ActionGraphConfigurationException(
                    "Optional goal parameter " + context.parameterName()
                            + " must declare a concrete value type"));
            Object converted = convertNonOptional(raw, valueType, converterType,
                    new GoalParameterBindingContext(context.parameterName(), valueType,
                            context.parameters(), context.blackboard()));
            return Optional.ofNullable(converted);
        }
        return convertNonOptional(raw, targetType, converterType, context);
    }

    private Object convertNonOptional(
            String raw,
            Class<?> targetType,
            Class<? extends GoalValueConverter<?>> converterType,
            GoalParameterBindingContext context
    ) {
        if (!converterType.equals(GoalValueConverter.None.class)) {
            Object converted = converterResolver.resolve(converterType).convert(raw, context);
            return castConverted(targetType, converted, context.parameterName());
        }
        Optional<GoalValueConverter<?>> typedConverter = converterResolver.resolveForType(wrapperType(targetType));
        if (typedConverter.isPresent()) {
            Object converted = typedConverter.orElseThrow().convert(raw, context);
            return castConverted(targetType, converted, context.parameterName());
        }
        return builtInConvert(raw, targetType, context.parameterName());
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
                        + "; use @FromGoalParam(converter=...), @GoalParameter(converter=...), or a "
                        + "TypedGoalValueConverter");
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

    private Object castConverted(Class<?> targetType, Object converted, String parameterName) {
        if (converted == null) {
            if (targetType.isPrimitive()) {
                throw new ActionGraphInputException(
                        "Goal converter returned null for primitive parameter: " + parameterName);
            }
            return null;
        }
        Class<?> type = wrapperType(targetType);
        if (!type.isInstance(converted)) {
            throw new ActionGraphConfigurationException(
                    "Goal converter returned " + converted.getClass().getName()
                            + " for parameter " + parameterName + ", expected " + type.getName());
        }
        return converted;
    }

    private <T> T instantiateRecord(Class<T> targetType, Class<?>[] constructorTypes, Object[] args) {
        try {
            Constructor<T> constructor = targetType.getDeclaredConstructor(constructorTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (NoSuchMethodException ex) {
            throw new ActionGraphConfigurationException(
                    "Cannot find canonical record constructor for " + targetType.getName(),
                    ex
            );
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new ActionGraphConfigurationException(
                    "Cannot instantiate bound goal parameter record " + targetType.getName(),
                    ex
            );
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ActionGraphIntegrationException(
                    "Bound goal parameter record constructor failed: " + targetType.getName(),
                    cause
            );
        }
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

    private String goalParamName(RecordComponent component, GoalParameter annotation) {
        if (annotation == null) {
            return component.getName();
        }
        return firstNonBlank(annotation.name(), annotation.value(), component.getName());
    }

    private boolean isRequired(GoalParameter annotation) {
        return annotation == null || annotation.required();
    }

    private Optional<Class<?>> optionalValueType(Type type) {
        if (!(type instanceof ParameterizedType parameterizedType)) {
            return Optional.empty();
        }
        Type raw = parameterizedType.getRawType();
        if (!raw.equals(Optional.class)) {
            return Optional.empty();
        }
        Type value = parameterizedType.getActualTypeArguments()[0];
        if (value instanceof Class<?> valueClass) {
            return Optional.of(valueClass);
        }
        return Optional.empty();
    }

    private Class<?> bindingContextType(Class<?> targetType, Type genericType) {
        if (targetType.equals(Optional.class)) {
            return optionalValueType(genericType).orElse(targetType);
        }
        return targetType;
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
