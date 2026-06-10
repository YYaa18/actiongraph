package com.actiongraph.samples.ordercancellation.domain;

public record OrderRecord(OrderId orderId, String status, boolean shipped) {
}
