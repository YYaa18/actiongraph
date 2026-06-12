package com.actiongraph.interpretation.annotation;

import com.actiongraph.api.Experimental;

/**
 * Converts a raw goal parameter value into a seeder method argument.
 *
 * @param <T> converted value type
 */
@FunctionalInterface
@Experimental(
        since = "0.2.0",
        value = "Custom goal value converters are experimental until annotated seeding pilots settle."
)
public interface GoalValueConverter<T> {
    T convert(String rawValue, GoalParameterBindingContext context);

    /**
     * Marker converter meaning "use ActionGraph's built-in type conversion".
     */
    final class None implements GoalValueConverter<Object> {
        @Override
        public Object convert(String rawValue, GoalParameterBindingContext context) {
            return rawValue;
        }
    }
}
