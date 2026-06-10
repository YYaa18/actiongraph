package com.actiongraph.samples.renewal.service;

import com.actiongraph.samples.renewal.domain.CustomerId;
import com.actiongraph.samples.renewal.domain.CustomerProfile;

public interface CustomerService {
    CustomerProfile findProfile(CustomerId customerId);
}
