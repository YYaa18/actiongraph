package com.actiongraph.interpretation.annotation;

import com.actiongraph.api.Experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares GoalCatalog metadata without writing a GoalDefinition factory.
 *
 * <p>On methods, parameters are inferred from the method signature unless
 * {@link #schema()} points at a record/class schema. On types, the annotated
 * type itself is used as the parameter schema.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Experimental(
        since = "0.2.0",
        value = "Annotated goal metadata is experimental until DX pilots validate schema conventions."
)
public @interface ActionGraphGoal {
    String type();

    String description() default "";

    String name() default "";

    String[] targetConditions();

    String[] seedConditions() default {};

    Class<?> schema() default Void.class;
}
