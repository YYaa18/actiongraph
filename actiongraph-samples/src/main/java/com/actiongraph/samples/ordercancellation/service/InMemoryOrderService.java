package com.actiongraph.samples.ordercancellation.service;

import com.actiongraph.samples.ordercancellation.domain.OrderId;
import com.actiongraph.samples.ordercancellation.domain.OrderRecord;

public final class InMemoryOrderService implements OrderService {
    private final boolean shipped;

    public InMemoryOrderService() {
        this(false);
    }

    public InMemoryOrderService(boolean shipped) {
        this.shipped = shipped;
    }

    @Override
    public OrderRecord findOrder(OrderId orderId) {
        return new OrderRecord(orderId, shipped ? "SHIPPED" : "PAID", shipped);
    }
}
