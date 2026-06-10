package com.actiongraph.samples.renewal.service;

import com.actiongraph.samples.renewal.domain.CurrentContract;
import com.actiongraph.samples.renewal.domain.CustomerId;

public interface ContractService {
    default boolean hasCurrent(CustomerId customerId) {
        return true;
    }

    CurrentContract findCurrent(CustomerId customerId);
}
