package com.actiongraph.action.annotation;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.exception.ActionGraphInputException;
import com.actiongraph.exception.ActionGraphIntegrationException;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.BlackboardKey;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class AnnotatedActionFactory {
    private AnnotatedActionFactory() {
    }

    public static List<Action> actions(Object... targets) {
        Map<String, AnnotatedMethod> actionMethods = new LinkedHashMap<>();
        Map<String, AnnotatedMethod> guardMethods = new LinkedHashMap<>();
        Map<String, AnnotatedMethod> compensationMethods = new LinkedHashMap<>();

        for (Object target : targets) {
            for (Method method : allMethods(target.getClass())) {
                method.setAccessible(true);
                ActionGraphAction action = method.getAnnotation(ActionGraphAction.class);
                if (action != null) {
                    validateActionReturnType(method, action.id());
                    putUnique(actionMethods, action.id(), new AnnotatedMethod(target, method), "action");
                }
                ActionGraphGuard guard = method.getAnnotation(ActionGraphGuard.class);
                if (guard != null) {
                    putUnique(guardMethods, guard.actionId(), new AnnotatedMethod(target, method), "runtime guard");
                }
                ActionGraphCompensation compensation = method.getAnnotation(ActionGraphCompensation.class);
                if (compensation != null) {
                    putUnique(compensationMethods, compensation.actionId(), new AnnotatedMethod(target, method),
                            "compensation");
                }
            }
        }

        return actionMethods.entrySet().stream()
                .map(entry -> new AnnotatedMethodAction(
                        entry.getValue(),
                        guardMethods.get(entry.getKey()),
                        compensationMethods.get(entry.getKey())
                ))
                .sorted(Comparator.comparing(action -> action.id().value()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static ActionRegistry registry(Object... targets) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        actions(targets).forEach(registry::register);
        return registry;
    }

    private static void putUnique(
            Map<String, AnnotatedMethod> methods,
            String actionId,
            AnnotatedMethod method,
            String kind
    ) {
        if (actionId == null || actionId.isBlank()) {
            throw new IllegalArgumentException("Annotated " + kind + " actionId must not be blank");
        }
        AnnotatedMethod previous = methods.putIfAbsent(actionId, method);
        if (previous != null) {
            throw new ActionGraphConfigurationException(
                    "Duplicate annotated " + kind + " for action id: " + actionId);
        }
    }

    private static void validateActionReturnType(Method method, String actionId) {
        Class<?> returnType = method.getReturnType();
        if (returnType.equals(Void.TYPE) || returnType.equals(ActionResult.class)) {
            return;
        }
        if (returnType.isInterface() || Modifier.isAbstract(returnType.getModifiers())) {
            throw new IllegalArgumentException(
                    "Annotated action " + actionId + " must return a concrete type, void, or ActionResult. "
                            + "Abstract/interface return type " + returnType.getName()
                            + " cannot be written to the Blackboard by declared type.");
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

    private record AnnotatedMethod(Object target, Method method) {
    }

    private static final class AnnotatedMethodAction implements Action {
        private final AnnotatedMethod actionMethod;
        private final AnnotatedMethod guardMethod;
        private final AnnotatedMethod compensationMethod;
        private final ActionGraphAction metadata;

        private AnnotatedMethodAction(
                AnnotatedMethod actionMethod,
                AnnotatedMethod guardMethod,
                AnnotatedMethod compensationMethod
        ) {
            this.actionMethod = actionMethod;
            this.guardMethod = guardMethod;
            this.compensationMethod = compensationMethod;
            this.metadata = actionMethod.method().getAnnotation(ActionGraphAction.class);
        }

        @Override
        public ActionId id() {
            return new ActionId(metadata.id());
        }

        @Override
        public Set<Class<?>> inputTypes() {
            return Set.copyOf(Arrays.asList(actionMethod.method().getParameterTypes()));
        }

        @Override
        public Set<Class<?>> outputTypes() {
            Class<?> returnType = actionMethod.method().getReturnType();
            if (returnType.equals(Void.TYPE) || returnType.equals(ActionResult.class)) {
                return Set.of();
            }
            return Set.of(returnType);
        }

        @Override
        public Set<Condition> preconditions() {
            return conditions(metadata.preconditions());
        }

        @Override
        public Set<Condition> effects() {
            return conditions(metadata.effects());
        }

        @Override
        public int cost() {
            return metadata.cost();
        }

        @Override
        public ActionRiskLevel riskLevel() {
            return metadata.riskLevel();
        }

        @Override
        public boolean requiresHumanReview() {
            return metadata.requiresHumanReview();
        }

        @Override
        public boolean runtimeGuard(Blackboard blackboard) {
            if (guardMethod == null) {
                return true;
            }
            Optional<Object[]> args = resolveArguments(guardMethod.method(), blackboard, false);
            if (args.isEmpty()) {
                return false;
            }
            Object result = invoke(guardMethod, args.get());
            if (result instanceof Boolean allowed) {
                return allowed;
            }
            throw new ActionGraphConfigurationException("Runtime guard must return boolean for action " + id().value());
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            Object[] args = resolveArguments(actionMethod.method(), context.blackboard(), true)
                    .orElseThrow(() -> new ActionGraphInputException("Missing action input for " + id().value()));
            Object result = invoke(actionMethod, args);
            if (result == null || actionMethod.method().getReturnType().equals(Void.TYPE)) {
                return ActionResult.ok();
            }
            if (result instanceof ActionResult actionResult) {
                return actionResult;
            }
            writeResult(context.blackboard(), actionMethod.method(), result);
            return ActionResult.ok();
        }

        @Override
        public CompensationResult compensate(ExecutionContext context) {
            if (compensationMethod == null) {
                return CompensationResult.noop();
            }
            Optional<Object[]> args = resolveArguments(compensationMethod.method(), context.blackboard(), false);
            if (args.isEmpty()) {
                return CompensationResult.noop();
            }
            Object result = invoke(compensationMethod, args.get());
            if (result instanceof CompensationResult compensationResult) {
                return compensationResult;
            }
            return CompensationResult.ok("Compensated " + id().value());
        }

        private Set<Condition> conditions(String[] keys) {
            Set<Condition> conditions = new LinkedHashSet<>();
            for (String key : keys) {
                conditions.add(Condition.of(key));
            }
            return Set.copyOf(conditions);
        }

        private Optional<Object[]> resolveArguments(Method method, Blackboard blackboard, boolean required) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                Optional<?> value = resolveArgument(parameters[i], parameterType, blackboard);
                if (value.isEmpty()) {
                    if (required) {
                        throw new ActionGraphInputException("Missing blackboard value for " + parameterType.getName());
                    }
                    return Optional.empty();
                }
                args[i] = value.get();
            }
            return Optional.of(args);
        }

        private Optional<?> resolveArgument(Parameter parameter, Class<?> parameterType, Blackboard blackboard) {
            BlackboardValue key = parameter.getAnnotation(BlackboardValue.class);
            if (key == null) {
                return blackboard.get(parameterType);
            }
            return getByKey(blackboard, parameterType, key.value());
        }

        private <T> Optional<T> getByKey(Blackboard blackboard, Class<T> type, String id) {
            return blackboard.get(BlackboardKey.of(type, id));
        }

        private void writeResult(Blackboard blackboard, Method method, Object result) {
            BlackboardValue key = method.getAnnotation(BlackboardValue.class);
            if (key == null) {
                blackboard.put(result);
                return;
            }
            writeResultByKey(blackboard, method.getReturnType(), key.value(), result);
        }

        private <T> void writeResultByKey(Blackboard blackboard, Class<T> type, String id, Object result) {
            blackboard.put(BlackboardKey.of(type, id), type.cast(result));
        }

        private Object invoke(AnnotatedMethod annotatedMethod, Object[] args) {
            try {
                return annotatedMethod.method().invoke(annotatedMethod.target(), args);
            } catch (IllegalAccessException ex) {
                throw new ActionGraphConfigurationException("Cannot access annotated action method", ex);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new ActionGraphIntegrationException("Annotated action method failed", cause);
            }
        }
    }
}
