package com.actiongraph.samples.claimsprecheck.service;

import com.actiongraph.samples.claimsprecheck.domain.ClaimDocumentBundle;
import com.actiongraph.samples.claimsprecheck.domain.ClaimPrecheckResult;
import com.actiongraph.samples.claimsprecheck.domain.ClaimRecord;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class InMemoryClaimPrecheckService implements ClaimPrecheckService {
    private static final Set<String> REQUIRED_DOCUMENTS = Set.of("claim-form", "medical-report", "invoice");

    @Override
    public ClaimPrecheckResult evaluate(ClaimRecord claim, ClaimDocumentBundle documents) {
        if (claim.closed()) {
            return new ClaimPrecheckResult(false, List.of("claim-reopened-approval"));
        }
        List<String> missing = REQUIRED_DOCUMENTS.stream()
                .filter(required -> !documents.documentTypes().contains(required))
                .sorted(Comparator.naturalOrder())
                .toList();
        return new ClaimPrecheckResult(missing.isEmpty(), missing);
    }
}
