package com.actiongraph.spring.security;

import com.actiongraph.controlplane.auth.ControlPlaneTokenVerifier;
import com.actiongraph.controlplane.auth.ForbiddenControlPlaneAccessException;
import com.actiongraph.controlplane.auth.SharedSecretTokenProperties;
import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.spring.ActionGraphProperties;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

public final class ActionGraphEndpointAccessVerifier {
    private static final String RESOURCE_SERVER_FILTER =
            "org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter";

    private final ActionGraphProperties properties;
    private final RunPrincipalResolver principalResolver;
    private final BooleanSupplier resourceServerAvailable;
    private final ControlPlaneTokenVerifier tokenVerifier = new ControlPlaneTokenVerifier();

    public ActionGraphEndpointAccessVerifier(
            ActionGraphProperties properties,
            RunPrincipalResolver principalResolver
    ) {
        this(properties, principalResolver, ActionGraphEndpointAccessVerifier::isResourceServerAvailable);
    }

    ActionGraphEndpointAccessVerifier(
            ActionGraphProperties properties,
            RunPrincipalResolver principalResolver,
            BooleanSupplier resourceServerAvailable
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.principalResolver = Objects.requireNonNull(principalResolver, "principalResolver");
        this.resourceServerAvailable = Objects.requireNonNull(resourceServerAvailable, "resourceServerAvailable");
        if (properties.getSecurity().getMode() == ActionGraphProperties.EndpointSecurityMode.OAUTH2) {
            assertResourceServerAvailable();
        }
    }

    public RunPrincipal verify(
            ActionGraphEndpointGroup group,
            @Nullable SharedSecretTokenProperties sharedSecretProperties,
            Function<String, @Nullable String> tokenLookup,
            String unauthorizedMessage
    ) {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(tokenLookup, "tokenLookup");
        if (properties.getSecurity().getMode() == ActionGraphProperties.EndpointSecurityMode.SHARED_SECRET) {
            if (sharedSecretProperties != null) {
                tokenVerifier.verify(sharedSecretProperties, tokenLookup, unauthorizedMessage);
            }
            return RunPrincipal.anonymous();
        }

        RunPrincipal principal = principalResolver.resolve();
        if (principal.anonymousPrincipal()) {
            throw new UnauthorizedControlPlaneAccessException(unauthorizedMessage);
        }
        List<String> requiredScopes = group.requiredScopes(properties.getSecurity().getEndpoints());
        if (!requiredScopes.isEmpty()) {
            Set<String> actualScopes = principalResolver.currentScopes();
            boolean allowed = requiredScopes.stream().anyMatch(actualScopes::contains);
            if (!allowed) {
                throw new ForbiddenControlPlaneAccessException(
                        "Missing required ActionGraph endpoint scope. Required any of " + requiredScopes);
            }
        }
        return principal;
    }

    private void assertResourceServerAvailable() {
        if (!resourceServerAvailable.getAsBoolean()) {
            throw new ActionGraphConfigurationException(
                    "actiongraph.security.mode=oauth2 requires spring-boot-starter-oauth2-resource-server "
                            + "on the application classpath");
        }
    }

    private static boolean isResourceServerAvailable() {
        try {
            Class.forName(RESOURCE_SERVER_FILTER);
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }
}
