package com.actiongraph.persistence.jdbc;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class BlackboardTypeRegistry {
    private final Set<String> allowedClassNames;
    private final Set<String> allowedPackagePrefixes;
    private final boolean allowAll;

    private BlackboardTypeRegistry(
            Set<String> allowedClassNames,
            Set<String> allowedPackagePrefixes,
            boolean allowAll
    ) {
        this.allowedClassNames = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(allowedClassNames, "allowedClassNames")
        ));
        this.allowedPackagePrefixes = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(allowedPackagePrefixes, "allowedPackagePrefixes")
        ));
        this.allowAll = allowAll;
    }

    public static BlackboardTypeRegistry allowAll() {
        return new BlackboardTypeRegistry(Set.of(), Set.of(), true);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean allows(String className) {
        String value = requireClassName(className);
        if (allowAll) {
            return true;
        }
        if (allowedClassNames.contains(value)) {
            return true;
        }
        return allowedPackagePrefixes.stream()
                .anyMatch(value::startsWith);
    }

    void verifyAllowed(String className) {
        if (!allows(className)) {
            throw new DisallowedBlackboardTypeException(className);
        }
    }

    public Set<String> allowedClassNames() {
        return allowedClassNames;
    }

    public Set<String> allowedPackagePrefixes() {
        return allowedPackagePrefixes;
    }

    public boolean allowAllTypes() {
        return allowAll;
    }

    private static String requireClassName(String className) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("blackboard type class name must not be blank");
        }
        return className;
    }

    private static String normalizePackagePrefix(String packagePrefix) {
        if (packagePrefix == null || packagePrefix.isBlank()) {
            throw new IllegalArgumentException("blackboard type package prefix must not be blank");
        }
        return packagePrefix.endsWith(".") ? packagePrefix : packagePrefix + ".";
    }

    public static final class Builder {
        private final Set<String> allowedClassNames = new LinkedHashSet<>();
        private final Set<String> allowedPackagePrefixes = new LinkedHashSet<>();

        private Builder() {
        }

        public Builder allowClass(Class<?> type) {
            Objects.requireNonNull(type, "type");
            return allowClassName(type.getName());
        }

        public Builder allowClassName(String className) {
            allowedClassNames.add(requireClassName(className));
            return this;
        }

        public Builder allowPackage(String packagePrefix) {
            allowedPackagePrefixes.add(normalizePackagePrefix(packagePrefix));
            return this;
        }

        public BlackboardTypeRegistry build() {
            return new BlackboardTypeRegistry(allowedClassNames, allowedPackagePrefixes, false);
        }
    }
}
