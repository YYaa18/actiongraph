package com.actiongraph.spring.security;

import com.actiongraph.spring.ActionGraphProperties;
import org.springframework.beans.factory.ObjectProvider;

public final class ActionGraphEndpointAccessVerifiers {
    private ActionGraphEndpointAccessVerifiers() {
    }

    public static ActionGraphEndpointAccessVerifier getOrSharedSecretDefault(
            ObjectProvider<ActionGraphEndpointAccessVerifier> provider
    ) {
        return provider.getIfAvailable(ActionGraphEndpointAccessVerifiers::sharedSecretDefault);
    }

    private static ActionGraphEndpointAccessVerifier sharedSecretDefault() {
        ActionGraphProperties properties = new ActionGraphProperties();
        return new ActionGraphEndpointAccessVerifier(
                properties,
                new SpringSecurityRunPrincipalResolver(properties.getSecurity().getOauth2())
        );
    }
}
