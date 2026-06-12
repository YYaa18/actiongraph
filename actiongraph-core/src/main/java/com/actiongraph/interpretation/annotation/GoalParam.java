package com.actiongraph.interpretation.annotation;

import com.actiongraph.api.Experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds an annotated goal seeder method parameter from {@code GoalParameters}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@Experimental(
        since = "0.2.0",
        value = "Annotated goal parameter binding is experimental until pilot domains validate conversion conventions."
)
public @interface GoalParam {
    String value() default "";

    String name() default "";

    boolean required() default true;

    Class<? extends GoalValueConverter<?>> converter() default GoalValueConverter.None.class;
}
