package com.actiongraph.samples.ordercancellation.service;

import com.actiongraph.samples.ordercancellation.domain.OrderId;
import com.actiongraph.samples.ordercancellation.domain.OrderRecord;

public interface OrderService {
    OrderRecord findOrder(OrderId orderId);
}
