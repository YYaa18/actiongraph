package com.actiongraph.samples.ordercancellation.domain;

public record CancellationEligibility(boolean eligible, String reason) {
}
