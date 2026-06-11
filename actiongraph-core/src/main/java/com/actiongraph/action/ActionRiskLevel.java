package com.actiongraph.action;

/**
 * Coarse business risk classification used by policy and human review.
 *
 * <p>The enum is intentionally small and stable. Domain-specific risk rules
 * should be represented by policy implementations or review attributes rather
 * than by adding values casually.
 */
public enum ActionRiskLevel {
    READ_ONLY,
    LOW,
    MEDIUM,
    HIGH
}
