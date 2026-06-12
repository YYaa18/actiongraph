package com.actiongraph.action.annotation;

import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.api.Experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ActionGraphAction {
    String id();

    @Experimental(
            since = "0.2.0",
            value = "Action descriptions are experimental until Goal Studio composition workflows settle."
    )
    String description() default "";

    String[] preconditions() default {};

    String[] effects() default {};

    int cost() default 1;

    ActionRiskLevel riskLevel() default ActionRiskLevel.LOW;

    boolean requiresHumanReview() default false;

    @Experimental(
            since = "0.1.0",
            value = "Retry configuration on annotated actions is experimental until idempotency conventions are proven in pilots."
    )
    int maxAttempts() default 1;

    @Experimental(
            since = "0.1.0",
            value = "Retry configuration on annotated actions is experimental until idempotency conventions are proven in pilots."
    )
    long backoffMillis() default 0;

    @Experimental(
            since = "0.1.0",
            value = "Timeout configuration on annotated actions is experimental until unknown-outcome compensation conventions are proven in pilots."
    )
    long timeoutMillis() default 0;
}
