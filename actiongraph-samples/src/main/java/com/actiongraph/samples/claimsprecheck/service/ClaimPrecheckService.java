package com.actiongraph.samples.claimsprecheck.service;

import com.actiongraph.samples.claimsprecheck.domain.ClaimDocumentBundle;
import com.actiongraph.samples.claimsprecheck.domain.ClaimPrecheckResult;
import com.actiongraph.samples.claimsprecheck.domain.ClaimRecord;

public interface ClaimPrecheckService {
    ClaimPrecheckResult evaluate(ClaimRecord claim, ClaimDocumentBundle documents);
}
