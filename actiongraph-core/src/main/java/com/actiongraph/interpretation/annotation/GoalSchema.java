package com.actiongraph.interpretation.annotation;

import com.actiongraph.api.Experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares binding-level validation rules for a goal schema record.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Experimental(
        since = "0.2.0",
        value = "Goal schema validation is experimental until DX3 pilots validate binding conventions."
)
public @interface GoalSchema {
    String[] atLeastOne() default {};
}
