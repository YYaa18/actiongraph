package com.actiongraph.samples.renewal.service;

import com.actiongraph.samples.renewal.domain.CustomerId;
import com.actiongraph.samples.renewal.domain.CustomerProfile;

public final class InMemoryCustomerService implements CustomerService {
    @Override
    public CustomerProfile findProfile(CustomerId customerId) {
        return new CustomerProfile(customerId, "Acme Corp");
    }
}
