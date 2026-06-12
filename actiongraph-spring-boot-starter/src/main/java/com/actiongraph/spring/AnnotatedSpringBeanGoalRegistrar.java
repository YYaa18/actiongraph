package com.actiongraph.spring;

import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.annotation.ActionGraphGoal;
import com.actiongraph.interpretation.annotation.AnnotatedGoalFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class AnnotatedSpringBeanGoalRegistrar {
    private final ConfigurableListableBeanFactory beanFactory;

    AnnotatedSpringBeanGoalRegistrar(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory");
    }

    List<GoalDefinition> annotatedGoals() {
        List<Object> targets = annotatedTargets();
        if (targets.isEmpty()) {
            return List.of();
        }
        return AnnotatedGoalFactory.definitions(targets.toArray());
    }

    private List<Object> annotatedTargets() {
        List<Object> targets = new ArrayList<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null || !hasActionGraphGoalAnnotations(beanType)) {
                continue;
            }
            targets.add(ClassUtils.getUserClass(beanType));
        }
        return targets;
    }

    private boolean hasActionGraphGoalAnnotations(Class<?> beanType) {
        Class<?> userClass = ClassUtils.getUserClass(beanType);
        if (AnnotatedElementUtils.hasAnnotation(userClass, ActionGraphGoal.class)) {
            return true;
        }
        for (Method method : ReflectionUtils.getAllDeclaredMethods(userClass)) {
            if (AnnotatedElementUtils.hasAnnotation(method, ActionGraphGoal.class)) {
                return true;
            }
        }
        return false;
    }
}
