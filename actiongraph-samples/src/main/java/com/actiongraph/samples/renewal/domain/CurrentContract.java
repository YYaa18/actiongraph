package com.actiongraph.samples.renewal.domain;

public record CurrentContract(String contractId, CustomerId customerId, boolean nearExpiry) {
}
