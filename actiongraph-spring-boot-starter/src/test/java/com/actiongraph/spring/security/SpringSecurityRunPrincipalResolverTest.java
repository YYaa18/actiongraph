package com.actiongraph.spring.security;

import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.spring.ActionGraphProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityRunPrincipalResolverTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mapsJwtClaimsIntoRunPrincipalAndScopes() {
        ActionGraphProperties.OAuth2Properties properties = new ActionGraphProperties.OAuth2Properties();
        properties.setRolesClaim("roles");
        Jwt jwt = new Jwt(
                "token",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(60),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "user:alice",
                        "azp", "portal-web",
                        "roles", List.of("maker", "approver"),
                        "scope", "actiongraph.runtime actiongraph.console"
                )
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(
                        new SimpleGrantedAuthority("SCOPE_actiongraph.runtime"),
                        new SimpleGrantedAuthority("ROLE_checker")
                )
        ));

        SpringSecurityRunPrincipalResolver resolver = new SpringSecurityRunPrincipalResolver(properties);

        RunPrincipal principal = resolver.resolve();
        assertThat(principal.subject()).isEqualTo("user:alice");
        assertThat(principal.clientId()).isEqualTo("portal-web");
        assertThat(principal.attributes()).containsEntry("roles", "maker,approver,checker");
        assertThat(resolver.currentScopes()).containsExactlyInAnyOrder(
                "actiongraph.runtime",
                "actiongraph.console"
        );
    }

    @Test
    void returnsAnonymousWhenSpringSecurityContextIsEmpty() {
        SpringSecurityRunPrincipalResolver resolver =
                new SpringSecurityRunPrincipalResolver(new ActionGraphProperties.OAuth2Properties());

        assertThat(resolver.resolve()).isEqualTo(RunPrincipal.anonymous());
        assertThat(resolver.currentScopes()).isEmpty();
    }
}
