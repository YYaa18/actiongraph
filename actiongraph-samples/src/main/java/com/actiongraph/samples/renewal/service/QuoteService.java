package com.actiongraph.samples.renewal.service;

import com.actiongraph.samples.renewal.domain.CustomerProfile;
import com.actiongraph.samples.renewal.domain.QuoteDraft;
import com.actiongraph.samples.renewal.domain.RenewalEligibility;

public interface QuoteService {
    QuoteDraft createDraft(CustomerProfile profile, RenewalEligibility eligibility);

    void voidDraft(String quoteId);
}
