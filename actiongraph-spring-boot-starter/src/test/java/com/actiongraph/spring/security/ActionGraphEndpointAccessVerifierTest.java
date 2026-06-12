package com.actiongraph.spring.security;

import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.spring.ActionGraphProperties;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionGraphEndpointAccessVerifierTest {
    @Test
    void oauth2ModeFailsFastWhenResourceServerDependencyIsMissing() {
        ActionGraphProperties properties = new ActionGraphProperties();
        properties.getSecurity().setMode(ActionGraphProperties.EndpointSecurityMode.OAUTH2);

        assertThatThrownBy(() -> new ActionGraphEndpointAccessVerifier(
                properties,
                new StaticRunPrincipalResolver(RunPrincipal.of("user:alice"), Set.of("actiongraph.runtime")),
                () -> false
        ))
                .isInstanceOf(ActionGraphConfigurationException.class)
                .hasMessageContaining("spring-boot-starter-oauth2-resource-server");
    }

    @Test
    void oauth2ModeRejectsAnonymousPrincipalAsUnauthorized() {
        ActionGraphProperties properties = new ActionGraphProperties();
        properties.getSecurity().setMode(ActionGraphProperties.EndpointSecurityMode.OAUTH2);
        ActionGraphEndpointAccessVerifier verifier = new ActionGraphEndpointAccessVerifier(
                properties,
                new StaticRunPrincipalResolver(RunPrincipal.anonymous(), Set.of("actiongraph.runtime")),
                () -> true
        );

        assertThatThrownBy(() -> verifier.verify(
                ActionGraphEndpointGroup.RUNTIME_API,
                null,
                ignored -> null,
                "unauthorized"
        ))
                .isInstanceOf(UnauthorizedControlPlaneAccessException.class)
                .hasMessageContaining("unauthorized");
    }

    private record StaticRunPrincipalResolver(
            RunPrincipal principal,
            Set<String> scopes
    ) implements RunPrincipalResolver {
        @Override
        public RunPrincipal resolve() {
            return principal;
        }

        @Override
        public Set<String> currentScopes() {
            return scopes;
        }
    }
}
