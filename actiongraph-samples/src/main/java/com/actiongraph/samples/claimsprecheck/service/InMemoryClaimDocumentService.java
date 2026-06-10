package com.actiongraph.samples.claimsprecheck.service;

import com.actiongraph.samples.claimsprecheck.domain.ClaimDocumentBundle;
import com.actiongraph.samples.claimsprecheck.domain.ClaimRecord;

import java.util.Set;

public final class InMemoryClaimDocumentService implements ClaimDocumentService {
    private final boolean missingInvoice;

    public InMemoryClaimDocumentService() {
        this(false);
    }

    public InMemoryClaimDocumentService(boolean missingInvoice) {
        this.missingInvoice = missingInvoice;
    }

    @Override
    public ClaimDocumentBundle documentsFor(ClaimRecord claim) {
        Set<String> documents = missingInvoice
                ? Set.of("claim-form", "medical-report")
                : Set.of("claim-form", "medical-report", "invoice");
        return new ClaimDocumentBundle(claim.claimId(), documents);
    }
}
