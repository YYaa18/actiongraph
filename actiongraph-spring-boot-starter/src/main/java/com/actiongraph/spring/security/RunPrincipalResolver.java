package com.actiongraph.spring.security;

import com.actiongraph.api.Experimental;
import com.actiongraph.identity.RunPrincipal;

import java.util.Set;

/**
 * Resolves the current Spring-side caller into ActionGraph's explicit run
 * principal model.
 */
@Experimental(
        since = "0.2.0",
        value = "Spring principal resolution is experimental until STD1 identity pilots settle."
)
public interface RunPrincipalResolver {
    RunPrincipal resolve();

    default Set<String> currentScopes() {
        return Set.of();
    }
}
