package com.actiongraph.policy;

public record ApprovalStage(String name, String requiredRole) {
    public ApprovalStage {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("approval stage name must not be blank");
        }
        requiredRole = requiredRole == null ? "" : requiredRole;
    }
}
