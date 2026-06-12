package com.actiongraph.spring;

import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.annotation.ActionGraphGoalSeeder;
import com.actiongraph.interpretation.annotation.AnnotatedGoalSeederFactory;
import com.actiongraph.interpretation.annotation.GoalValueConverter;
import com.actiongraph.interpretation.annotation.GoalValueConverterResolver;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class AnnotatedSpringBeanGoalSeederRegistrar {
    private final ConfigurableListableBeanFactory beanFactory;

    AnnotatedSpringBeanGoalSeederRegistrar(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory");
    }

    List<GoalBlackboardSeeder> annotatedSeeders() {
        List<Object> targets = annotatedTargets();
        if (targets.isEmpty()) {
            return List.of();
        }
        return AnnotatedGoalSeederFactory.seeders(converterResolver(), targets.toArray());
    }

    private List<Object> annotatedTargets() {
        List<Object> targets = new ArrayList<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null || !hasActionGraphGoalSeederAnnotations(beanType)) {
                continue;
            }
            targets.add(beanFactory.getBean(beanName));
        }
        return targets;
    }

    private boolean hasActionGraphGoalSeederAnnotations(Class<?> beanType) {
        Class<?> userClass = ClassUtils.getUserClass(beanType);
        for (Method method : ReflectionUtils.getAllDeclaredMethods(userClass)) {
            if (AnnotatedElementUtils.hasAnnotation(method, ActionGraphGoalSeeder.class)) {
                return true;
            }
        }
        return false;
    }

    private GoalValueConverterResolver converterResolver() {
        GoalValueConverterResolver reflection = GoalValueConverterResolver.reflection();
        return converterType -> {
            String[] beanNames = beanFactory.getBeanNamesForType(converterType, true, false);
            if (beanNames.length > 0) {
                return beanFactory.getBean(converterType);
            }
            return reflection.resolve(converterType);
        };
    }
}
