package com.actiongraph.spring;

import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.annotation.ActionGraphAction;
import com.actiongraph.action.annotation.ActionGraphCompensation;
import com.actiongraph.action.annotation.ActionGraphGuard;
import com.actiongraph.action.annotation.AnnotatedActionFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class AnnotatedSpringBeanActionRegistrar {
    private final ConfigurableListableBeanFactory beanFactory;

    AnnotatedSpringBeanActionRegistrar(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory");
    }

    void registerAnnotatedActions(DefaultActionRegistry registry) {
        List<Object> targets = annotatedTargets();
        if (targets.isEmpty()) {
            return;
        }
        for (Action action : AnnotatedActionFactory.actions(targets.toArray())) {
            registry.register(action);
        }
    }

    private List<Object> annotatedTargets() {
        List<Object> targets = new ArrayList<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null || !hasActionGraphAnnotations(beanType)) {
                continue;
            }
            targets.add(beanFactory.getBean(beanName));
        }
        return targets;
    }

    private boolean hasActionGraphAnnotations(Class<?> beanType) {
        Class<?> userClass = ClassUtils.getUserClass(beanType);
        for (Method method : ReflectionUtils.getAllDeclaredMethods(userClass)) {
            if (AnnotatedElementUtils.hasAnnotation(method, ActionGraphAction.class)
                    || AnnotatedElementUtils.hasAnnotation(method, ActionGraphGuard.class)
                    || AnnotatedElementUtils.hasAnnotation(method, ActionGraphCompensation.class)) {
                return true;
            }
        }
        return false;
    }
}
