package com.actiongraph.samples.claimsprecheck.domain;

import java.util.Set;

public record ClaimDocumentBundle(ClaimId claimId, Set<String> documentTypes) {
    public ClaimDocumentBundle {
        if (claimId == null) {
            throw new IllegalArgumentException("claimId must not be null");
        }
        documentTypes = Set.copyOf(documentTypes == null ? Set.of() : documentTypes);
    }
}
