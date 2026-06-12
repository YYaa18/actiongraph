package com.actiongraph.spring.security;

import com.actiongraph.spring.ActionGraphProperties;

import java.util.List;

public enum ActionGraphEndpointGroup {
    CONSOLE,
    STUDIO,
    RUNTIME_API,
    HUMAN_REVIEW,
    EVENTS;

    List<String> requiredScopes(ActionGraphProperties.EndpointScopesProperties endpoints) {
        return switch (this) {
            case CONSOLE -> endpoints.getConsole();
            case STUDIO -> endpoints.getStudio();
            case RUNTIME_API -> endpoints.getRuntimeApi();
            case HUMAN_REVIEW -> endpoints.getHumanReview();
            case EVENTS -> endpoints.getEvents();
        };
    }
}
