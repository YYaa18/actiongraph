package com.actiongraph.spring;

import com.actiongraph.api.Experimental;
import com.actiongraph.api.Internal;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.interpretation.annotation.GoalValueConverter;
import com.actiongraph.interpretation.annotation.GoalValueConverterResolver;
import com.actiongraph.interpretation.annotation.TypedGoalValueConverter;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Internal
@Experimental(
        since = "0.2.0",
        value = "Spring goal value converter resolution is internal plumbing while DX4 configuration conventions settle."
)
public final class SpringGoalValueConverterResolver implements GoalValueConverterResolver {
    private final ConfigurableListableBeanFactory beanFactory;
    private final GoalValueConverterResolver reflection;

    public SpringGoalValueConverterResolver(ConfigurableListableBeanFactory beanFactory) {
        this(beanFactory, GoalValueConverterResolver.reflection());
    }

    public SpringGoalValueConverterResolver(
            ConfigurableListableBeanFactory beanFactory,
            GoalValueConverterResolver reflection
    ) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory");
        this.reflection = Objects.requireNonNull(reflection, "reflection");
        assertUniqueTypedConverters();
    }

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
            throw duplicateTypedConverter(targetType, matches.stream()
                    .map(converter -> converter.getClass().getName())
                    .toList());
        }
        return matches.stream().findFirst().map(converter -> (GoalValueConverter<?>) converter);
    }

    private void assertUniqueTypedConverters() {
        String[] beanNames = beanFactory.getBeanNamesForType(TypedGoalValueConverter.class, true, false);
        java.util.Map<Class<?>, List<String>> sourcesByType = new java.util.LinkedHashMap<>();
        for (String beanName : beanNames) {
            TypedGoalValueConverter<?> converter = (TypedGoalValueConverter<?>) beanFactory.getBean(beanName);
            sourcesByType.computeIfAbsent(converter.targetType(), ignored -> new ArrayList<>())
                    .add(converter.getClass().getName());
        }
        sourcesByType.forEach((targetType, sources) -> {
            if (sources.size() > 1) {
                throw duplicateTypedConverter(targetType, sources);
            }
        });
    }

    private ActionGraphConfigurationException duplicateTypedConverter(Class<?> targetType, List<String> sources) {
        return new ActionGraphConfigurationException(
                "Multiple typed goal value converters registered for " + targetType.getName()
                        + ": " + sources.stream()
                        .sorted()
                        .collect(Collectors.joining(", ")));
    }
}
