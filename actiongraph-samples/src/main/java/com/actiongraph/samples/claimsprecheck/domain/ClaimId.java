package com.actiongraph.samples.claimsprecheck.domain;

public record ClaimId(String value) {
    public ClaimId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("claim id must not be blank");
        }
        value = value.trim().toUpperCase();
    }
}
