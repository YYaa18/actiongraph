package com.actiongraph.interpretation.annotation;

import com.actiongraph.api.Experimental;

/**
 * Converts goal parameter values for every binding target of a specific type.
 *
 * @param <T> converted value type
 */
@Experimental(
        since = "0.2.0",
        value = "Typed goal value converters are experimental until Spring pilot applications validate resolution rules."
)
public interface TypedGoalValueConverter<T> extends GoalValueConverter<T> {
    Class<T> targetType();
}
