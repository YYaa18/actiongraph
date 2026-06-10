package com.actiongraph.samples.claimsprecheck.domain;

import java.util.List;

public record ClaimPrecheckResult(boolean complete, List<String> missingDocuments) {
    public ClaimPrecheckResult {
        missingDocuments = List.copyOf(missingDocuments == null ? List.of() : missingDocuments);
    }
}
