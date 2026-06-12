package com.actiongraph.interpretation.annotation;

import com.actiongraph.api.Experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a method that seeds the initial Blackboard for a goal.
 *
 * <p>The annotated method is invoked by ActionGraph before planning starts.
 * Parameters may be bound from {@link GoalParam} values, or may request the
 * current {@code GoalParameters} / {@code Blackboard} directly.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Experimental(
        since = "0.2.0",
        value = "Annotated goal seeding is experimental until pilot domains validate binding conventions."
)
public @interface ActionGraphGoalSeeder {
    String value() default "";

    String goal() default "";

    String goalType() default "";

    String[] seedConditions() default {};
}
