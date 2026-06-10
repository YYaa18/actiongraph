package com.actiongraph.samples.claimsprecheck.service;

import com.actiongraph.samples.claimsprecheck.domain.ClaimId;
import com.actiongraph.samples.claimsprecheck.domain.ClaimRecord;

public interface ClaimService {
    ClaimRecord findClaim(ClaimId claimId);
}
