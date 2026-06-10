package com.actiongraph.samples.claimsprecheck.service;

import com.actiongraph.samples.claimsprecheck.domain.ClaimDocumentBundle;
import com.actiongraph.samples.claimsprecheck.domain.ClaimRecord;

public interface ClaimDocumentService {
    ClaimDocumentBundle documentsFor(ClaimRecord claim);
}
