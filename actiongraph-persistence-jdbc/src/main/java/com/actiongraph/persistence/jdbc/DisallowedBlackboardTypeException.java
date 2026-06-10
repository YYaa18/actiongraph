package com.actiongraph.persistence.jdbc;

public final class DisallowedBlackboardTypeException extends IllegalStateException {
    private final String className;

    public DisallowedBlackboardTypeException(String className) {
        super("Blackboard type is not allowed for JDBC snapshot restore: " + className);
        this.className = className;
    }

    public String className() {
        return className;
    }
}
