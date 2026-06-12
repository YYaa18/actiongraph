package com.actiongraph.spring.security;

import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.spring.ActionGraphProperties;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class SpringSecurityRunPrincipalResolver implements RunPrincipalResolver {
    private final ActionGraphProperties.OAuth2Properties properties;

    public SpringSecurityRunPrincipalResolver(ActionGraphProperties.OAuth2Properties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public RunPrincipal resolve() {
        Object authentication = currentAuthentication().orElse(null);
        if (authentication == null || !authenticated(authentication)) {
            return RunPrincipal.anonymous();
        }
        Map<String, Object> claims = tokenClaims(authentication);
        String subject = stringValue(claims.get("sub"))
                .or(() -> invokeString(authentication, "getName"))
                .filter(value -> !value.isBlank())
                .orElse(RunPrincipal.ANONYMOUS_SUBJECT);
        String clientId = stringValue(claims.get(properties.getClientIdClaim()))
                .or(() -> stringValue(claims.get(properties.getFallbackClientIdClaim())))
                .orElse("");
        Map<String, String> attributes = new LinkedHashMap<>();
        String roles = roles(claims, authentication);
        if (!roles.isBlank()) {
            attributes.put("roles", roles);
        }
        return new RunPrincipal(subject, clientId, delegationChain(claims), attributes);
    }

    @Override
    public Set<String> currentScopes() {
        Object authentication = currentAuthentication().orElse(null);
        if (authentication == null || !authenticated(authentication)) {
            return Set.of();
        }
        Set<String> scopes = new LinkedHashSet<>();
        for (String authority : authorities(authentication)) {
            if (authority.startsWith("SCOPE_")) {
                scopes.add(authority.substring("SCOPE_".length()));
            } else if (authority.startsWith("scp:")) {
                scopes.add(authority.substring("scp:".length()));
            }
        }
        Map<String, Object> claims = tokenClaims(authentication);
        addDelimited(scopes, claims.get("scope"));
        addCollection(scopes, claims.get("scp"));
        return Set.copyOf(scopes);
    }

    private Optional<Object> currentAuthentication() {
        try {
            Class<?> holderType = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object context = holderType.getMethod("getContext").invoke(null);
            if (context == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(context.getClass().getMethod("getAuthentication").invoke(context));
        } catch (ReflectiveOperationException | LinkageError ex) {
            return Optional.empty();
        }
    }

    private boolean authenticated(Object authentication) {
        try {
            Object authenticated = authentication.getClass().getMethod("isAuthenticated").invoke(authentication);
            return Boolean.TRUE.equals(authenticated);
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tokenClaims(Object authentication) {
        Object tokenAttributes = invoke(authentication, "getTokenAttributes").orElse(null);
        if (tokenAttributes instanceof Map<?, ?> rawMap) {
            return rawMap.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            entry -> String.valueOf(entry.getKey()),
                            Map.Entry::getValue
                    ));
        }
        Object token = invoke(authentication, "getToken").orElse(null);
        if (token != null) {
            Object claims = invoke(token, "getClaims").orElse(null);
            if (claims instanceof Map<?, ?> rawMap) {
                return rawMap.entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(
                                entry -> String.valueOf(entry.getKey()),
                                Map.Entry::getValue
                        ));
            }
        }
        return Map.of();
    }

    private String roles(Map<String, Object> claims, Object authentication) {
        Set<String> roles = new LinkedHashSet<>();
        addCollection(roles, claims.get(properties.getRolesClaim()));
        addDelimited(roles, claims.get(properties.getRolesClaim()));
        for (String authority : authorities(authentication)) {
            if (authority.startsWith("ROLE_")) {
                roles.add(authority.substring("ROLE_".length()));
            }
        }
        return String.join(",", roles);
    }

    private List<String> delegationChain(Map<String, Object> claims) {
        String claim = properties.getDelegationChainClaim();
        if (claim == null || claim.isBlank()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        addCollection(values, claims.get(claim));
        addDelimited(values, claims.get(claim));
        return List.copyOf(values);
    }

    private Collection<String> authorities(Object authentication) {
        Object rawAuthorities = invoke(authentication, "getAuthorities").orElse(List.of());
        if (!(rawAuthorities instanceof Collection<?> collection)) {
            return List.of();
        }
        List<String> authorities = new ArrayList<>();
        for (Object authority : collection) {
            invokeString(authority, "getAuthority")
                    .filter(value -> !value.isBlank())
                    .ifPresent(authorities::add);
        }
        return authorities;
    }

    private static Optional<Object> invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException | LinkageError ex) {
            return Optional.empty();
        }
    }

    private static Optional<String> invokeString(Object target, String methodName) {
        return invoke(target, methodName).flatMap(SpringSecurityRunPrincipalResolver::stringValue);
    }

    private static Optional<String> stringValue(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    private static void addCollection(Set<String> target, Object value) {
        if (value instanceof Collection<?> collection) {
            collection.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .forEach(target::add);
        }
    }

    private static void addDelimited(Set<String> target, Object value) {
        if (value instanceof Collection<?>) {
            return;
        }
        stringValue(value).ifPresent(raw -> {
            for (String item : raw.split("[, ]")) {
                String safeItem = item.trim();
                if (!safeItem.isBlank()) {
                    target.add(safeItem);
                }
            }
        });
    }
}
