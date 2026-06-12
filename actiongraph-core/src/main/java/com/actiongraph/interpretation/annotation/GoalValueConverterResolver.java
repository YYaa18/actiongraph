package com.actiongraph.interpretation.annotation;

import com.actiongraph.api.Experimental;
import com.actiongraph.exception.ActionGraphConfigurationException;

import java.lang.reflect.InvocationTargetException;

/**
 * Resolves converter classes declared by {@link GoalParam}.
 */
@FunctionalInterface
@Experimental(
        since = "0.2.0",
        value = "Goal value converter resolution is experimental until annotated seeding pilots settle."
)
public interface GoalValueConverterResolver {
    GoalValueConverter<?> resolve(Class<? extends GoalValueConverter<?>> converterType);

    static GoalValueConverterResolver reflection() {
        return converterType -> {
            try {
                var constructor = converterType.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (NoSuchMethodException ex) {
                throw new ActionGraphConfigurationException(
                        "Goal value converter " + converterType.getName()
                                + " must be a Spring bean or expose a no-arg constructor",
                        ex
                );
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                throw new ActionGraphConfigurationException(
                        "Cannot instantiate goal value converter " + converterType.getName(),
                        ex
                );
            }
        };
    }
}
