package com.actiongraph.planning;

public record Condition(String key) {
    public Condition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Condition key must not be blank");
        }
        if (!key.equals(key.trim())) {
            throw new IllegalArgumentException("Condition key must not contain leading or trailing whitespace");
        }
    }

    public static Condition of(String key) {
        return new Condition(key);
    }

    public static Condition of(String namespace, String key) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("Condition namespace must not be blank");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Condition key must not be blank");
        }
        String normalizedNamespace = namespace.trim();
        String normalizedKey = key.trim();
        if (normalizedNamespace.contains(":")) {
            throw new IllegalArgumentException("Condition namespace must not contain ':'");
        }
        if (normalizedKey.contains(":")) {
            throw new IllegalArgumentException("Condition key must not contain ':' when namespace is provided");
        }
        return new Condition(normalizedNamespace + ":" + normalizedKey);
    }

    public String namespace() {
        int split = key.indexOf(':');
        return split < 0 ? "" : key.substring(0, split);
    }

    public String name() {
        int split = key.indexOf(':');
        return split < 0 ? key : key.substring(split + 1);
    }
}
