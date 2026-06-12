package com.actiongraph.interpretation.annotation;

import com.actiongraph.api.Experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes a parameter inferred for an annotated goal schema.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.FIELD})
@Experimental(
        since = "0.2.0",
        value = "Annotated goal metadata is experimental until DX pilots validate schema conventions."
)
public @interface GoalParameter {
    String value() default "";

    String name() default "";

    String description() default "";

    boolean required() default true;

    String example() default "";

    Class<? extends GoalValueConverter<?>> converter() default GoalValueConverter.None.class;
}
