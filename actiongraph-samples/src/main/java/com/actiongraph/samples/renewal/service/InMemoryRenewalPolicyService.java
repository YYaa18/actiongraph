package com.actiongraph.samples.renewal.service;

import com.actiongraph.samples.renewal.domain.CurrentContract;
import com.actiongraph.samples.renewal.domain.RenewalEligibility;

public final class InMemoryRenewalPolicyService implements RenewalPolicyService {
    private final boolean eligible;
    private final String reason;

    public InMemoryRenewalPolicyService() {
        this(true, "near expiry");
    }

    public InMemoryRenewalPolicyService(boolean eligible, String reason) {
        this.eligible = eligible;
        this.reason = reason;
    }

    @Override
    public RenewalEligibility check(CurrentContract contract) {
        return new RenewalEligibility(eligible && contract.nearExpiry(), reason);
    }
}
