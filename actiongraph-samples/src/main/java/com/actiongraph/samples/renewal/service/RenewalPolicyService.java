package com.actiongraph.samples.renewal.service;

import com.actiongraph.samples.renewal.domain.CurrentContract;
import com.actiongraph.samples.renewal.domain.RenewalEligibility;

public interface RenewalPolicyService {
    RenewalEligibility check(CurrentContract contract);
}
