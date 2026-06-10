package com.actiongraph.samples.claimsprecheck.service;

import com.actiongraph.samples.claimsprecheck.domain.ClaimId;
import com.actiongraph.samples.claimsprecheck.domain.ClaimRecord;

import java.math.BigDecimal;

public final class InMemoryClaimService implements ClaimService {
    private final boolean closed;
    private final BigDecimal claimedAmount;

    public InMemoryClaimService() {
        this(false);
    }

    public InMemoryClaimService(boolean closed) {
        this(closed, new BigDecimal("260000"));
    }

    public InMemoryClaimService(boolean closed, BigDecimal claimedAmount) {
        this.closed = closed;
        this.claimedAmount = claimedAmount;
    }

    @Override
    public ClaimRecord findClaim(ClaimId claimId) {
        return new ClaimRecord(
                claimId,
                "POLICY-2026-001",
                "Zhang San",
                claimedAmount,
                "CNY",
                closed
        );
    }
}
