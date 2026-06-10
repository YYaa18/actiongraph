package com.actiongraph.samples.ordercancellation;

import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.samples.ordercancellation.actions.CancellationEligibilityCheckAction;
import com.actiongraph.samples.ordercancellation.actions.CancellationRequestDraftAction;
import com.actiongraph.samples.ordercancellation.actions.OperationsApprovalRequestAction;
import com.actiongraph.samples.ordercancellation.actions.OrderLookupAction;
import com.actiongraph.samples.ordercancellation.service.CancellationPolicyService;
import com.actiongraph.samples.ordercancellation.service.CancellationRequestService;
import com.actiongraph.samples.ordercancellation.service.OperationsApprovalService;
import com.actiongraph.samples.ordercancellation.service.OrderService;

import java.util.List;

public final class OrderCancellationActionFactory {
    private OrderCancellationActionFactory() {
    }

    public static List<Action> actions(
            OrderService orderService,
            CancellationPolicyService policyService,
            CancellationRequestService requestService,
            OperationsApprovalService approvalService
    ) {
        return List.of(
                new OrderLookupAction(orderService),
                new CancellationEligibilityCheckAction(policyService),
                new CancellationRequestDraftAction(requestService),
                new OperationsApprovalRequestAction(approvalService)
        );
    }

    public static DefaultActionRegistry registry(List<Action> actions) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        actions.forEach(registry::register);
        return registry;
    }
}
