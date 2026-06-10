package com.actiongraph.samples.renewal.service;

import com.actiongraph.samples.renewal.domain.CurrentContract;
import com.actiongraph.samples.renewal.domain.CustomerId;

public final class InMemoryContractService implements ContractService {
    @Override
    public CurrentContract findCurrent(CustomerId customerId) {
        return new CurrentContract("CONTRACT-" + customerId.value(), customerId, true);
    }
}
