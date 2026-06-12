package com.actiongraph.interpretation.annotation;

import com.actiongraph.api.Experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a goal seeder method parameter from the full goal parameter set.
 *
 * <p>The first supported target shape is a record. Record components are bound
 * by name and may use {@link GoalParameter} to override the parameter name or
 * required flag.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@Experimental(
        since = "0.2.0",
        value = "Goal parameter object binding is experimental until product pilots validate schema conventions."
)
public @interface BindGoalParams {
    /**
     * Requires at least one component value to be present. This is useful for
     * partial update schemas where every individual field is optional, but an
     * empty patch is invalid.
     */
    boolean atLeastOne() default false;
}
