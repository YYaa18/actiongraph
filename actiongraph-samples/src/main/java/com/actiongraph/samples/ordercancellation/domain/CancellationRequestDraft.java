package com.actiongraph.samples.ordercancellation.domain;

public record CancellationRequestDraft(String requestId, OrderId orderId) {
}
