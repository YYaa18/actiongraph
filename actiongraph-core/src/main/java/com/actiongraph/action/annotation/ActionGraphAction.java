package com.actiongraph.action.annotation;

import com.actiongraph.action.ActionRiskLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ActionGraphAction {
    String id();

    String[] preconditions() default {};

    String[] effects() default {};

    int cost() default 1;

    ActionRiskLevel riskLevel() default ActionRiskLevel.LOW;

    boolean requiresHumanReview() default false;
}
