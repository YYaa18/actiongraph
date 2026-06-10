package com.actiongraph.samples.ordercancellation.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.samples.ordercancellation.OrderCancellationConditions;
import com.actiongraph.samples.ordercancellation.domain.OrderId;
import com.actiongraph.samples.ordercancellation.domain.OrderRecord;
import com.actiongraph.samples.ordercancellation.service.OrderService;
import com.actiongraph.planning.Condition;

import java.util.Set;

public final class OrderLookupAction implements Action {
    public static final ActionId ID = new ActionId("order.lookup");

    private final OrderService orderService;

    public OrderLookupAction(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(OrderId.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(OrderRecord.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(OrderCancellationConditions.ORDER_ID_PRESENT);
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(OrderCancellationConditions.ORDER_LOADED);
    }

    @Override
    public int cost() {
        return 1;
    }

    @Override
    public ActionRiskLevel riskLevel() {
        return ActionRiskLevel.READ_ONLY;
    }

    @Override
    public boolean requiresHumanReview() {
        return false;
    }

    @Override
    public ActionResult execute(ExecutionContext context) {
        OrderId orderId = context.blackboard().get(OrderId.class)
                .orElseThrow(() -> new IllegalStateException("OrderId missing"));
        context.blackboard().put(orderService.findOrder(orderId));
        return ActionResult.ok();
    }
}
