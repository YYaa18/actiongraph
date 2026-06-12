package com.actiongraph.spring;

import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.annotation.ActionGraphGoalSeeder;
import com.actiongraph.interpretation.annotation.AnnotatedGoalSeederFactory;
import com.actiongraph.interpretation.annotation.GoalValueConverter;
import com.actiongraph.interpretation.annotation.GoalValueConverterResolver;
import com.actiongraph.interpretation.annotation.TypedGoalValueConverter;
import com.actiongraph.exception.ActionGraphConfigurationException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class AnnotatedSpringBeanGoalSeederRegistrar {
    private final ConfigurableListableBeanFactory beanFactory;
    private final GoalCatalog catalog;

    AnnotatedSpringBeanGoalSeederRegistrar(ConfigurableListableBeanFactory beanFactory, GoalCatalog catalog) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    List<GoalBlackboardSeeder> annotatedSeeders() {
        List<Object> targets = annotatedTargets();
        if (targets.isEmpty()) {
            return List.of();
        }
        return AnnotatedGoalSeederFactory.seeders(converterResolver(), catalog.all(), targets.toArray());
    }

    private List<Object> annotatedTargets() {
        List<Object> targets = new ArrayList<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null || !hasActionGraphGoalSeederAnnotations(beanType)) {
                continue;
            }
            targets.add(beanFactory.getBean(beanName));
        }
        return targets;
    }

    private boolean hasActionGraphGoalSeederAnnotations(Class<?> beanType) {
        Class<?> userClass = ClassUtils.getUserClass(beanType);
        for (Method method : ReflectionUtils.getAllDeclaredMethods(userClass)) {
            if (AnnotatedElementUtils.hasAnnotation(method, ActionGraphGoalSeeder.class)) {
                return true;
            }
        }
        return false;
    }

    GoalValueConverterResolver converterResolver() {
        assertUniqueTypedConverters();
        GoalValueConverterResolver reflection = GoalValueConverterResolver.reflection();
        return new GoalValueConverterResolver() {
            @Override
            public GoalValueConverter<?> resolve(Class<? extends GoalValueConverter<?>> converterType) {
                String[] beanNames = beanFactory.getBeanNamesForType(converterType, true, false);
                if (beanNames.length > 0) {
                    return beanFactory.getBean(converterType);
                }
                return reflection.resolve(converterType);
            }

            @Override
            public Optional<GoalValueConverter<?>> resolveForType(Class<?> targetType) {
                String[] beanNames = beanFactory.getBeanNamesForType(TypedGoalValueConverter.class, true, false);
                List<TypedGoalValueConverter<?>> matches = new ArrayList<>();
                for (String beanName : beanNames) {
                    TypedGoalValueConverter<?> converter =
                            (TypedGoalValueConverter<?>) beanFactory.getBean(beanName);
                    if (targetType.equals(converter.targetType())) {
                        matches.add(converter);
                    }
                }
                if (matches.size() > 1) {
                    throw new ActionGraphConfigurationException(
                            "Multiple typed goal value converters registered for " + targetType.getName()
                                    + ": " + matches.stream()
                                    .map(converter -> converter.getClass().getName())
                                    .sorted()
                                    .collect(java.util.stream.Collectors.joining(", ")));
                }
                return matches.stream().findFirst().map(converter -> (GoalValueConverter<?>) converter);
            }
        };
    }

    private void assertUniqueTypedConverters() {
        String[] beanNames = beanFactory.getBeanNamesForType(TypedGoalValueConverter.class, true, false);
        Map<Class<?>, List<String>> sourcesByType = new LinkedHashMap<>();
        for (String beanName : beanNames) {
            TypedGoalValueConverter<?> converter = (TypedGoalValueConverter<?>) beanFactory.getBean(beanName);
            sourcesByType.computeIfAbsent(converter.targetType(), ignored -> new ArrayList<>())
                    .add(converter.getClass().getName());
        }
        sourcesByType.forEach((targetType, sources) -> {
            if (sources.size() > 1) {
                throw new ActionGraphConfigurationException(
                        "Multiple typed goal value converters registered for " + targetType.getName()
                                + ": " + sources.stream()
                                .sorted()
                                .collect(java.util.stream.Collectors.joining(", ")));
            }
        });
    }
}
