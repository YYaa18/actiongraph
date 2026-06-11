package demo;

import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.annotation.ActionGraphAction;
import com.actiongraph.action.annotation.ActionGraphCompensation;
import com.actiongraph.action.annotation.ActionGraphGuard;
import org.springframework.stereotype.Service;

@Service
final class OrderCancellationWorkflow {
    private final OrderService orderService;
    private final CancellationPolicyService policyService;
    private final CancellationRequestService requestService;
    private final OperationsApprovalService approvalService;

    OrderCancellationWorkflow(
            OrderService orderService,
            CancellationPolicyService policyService,
            CancellationRequestService requestService,
            OperationsApprovalService approvalService
    ) {
        this.orderService = orderService;
        this.policyService = policyService;
        this.requestService = requestService;
        this.approvalService = approvalService;
    }

    @ActionGraphAction(
            id = "order.lookup",
            preconditions = "order-cancellation:ORDER_ID_PRESENT",
            effects = "order-cancellation:ORDER_LOADED",
            riskLevel = ActionRiskLevel.READ_ONLY
    )
    OrderRecord lookup(OrderId orderId) {
        return orderService.find(orderId);
    }

    @ActionGraphAction(
            id = "order.cancellation.eligibility.check",
            preconditions = "order-cancellation:ORDER_LOADED",
            effects = "order-cancellation:CANCELLATION_ELIGIBILITY_CHECKED"
    )
    CancellationEligibility check(OrderRecord order) {
        return policyService.check(order);
    }

    @ActionGraphAction(
            id = "order.cancellation.request.draft",
            preconditions = {
                    "order-cancellation:ORDER_LOADED",
                    "order-cancellation:CANCELLATION_ELIGIBILITY_CHECKED"
            },
            effects = "order-cancellation:CANCELLATION_REQUEST_DRAFTED",
            riskLevel = ActionRiskLevel.MEDIUM,
            maxAttempts = 2,
            backoffMillis = 250
    )
    CancellationRequestDraft draft(OrderRecord order, CancellationEligibility eligibility) {
        return requestService.createDraft(order, eligibility);
    }

    @ActionGraphGuard(actionId = "order.cancellation.request.draft")
    boolean canDraft(CancellationEligibility eligibility) {
        return eligibility.eligible();
    }

    @ActionGraphCompensation(actionId = "order.cancellation.request.draft")
    void voidDraft(CancellationRequestDraft draft) {
        requestService.voidDraft(draft.requestId());
    }

    @ActionGraphAction(
            id = "operations.approval.request",
            preconditions = "order-cancellation:CANCELLATION_REQUEST_DRAFTED",
            effects = "order-cancellation:OPERATIONS_APPROVAL_REQUESTED",
            riskLevel = ActionRiskLevel.HIGH,
            requiresHumanReview = true
    )
    OperationsApprovalRequest requestApproval(CancellationRequestDraft draft) {
        return approvalService.requestApproval(draft);
    }
}

record OrderId(String value) {
}

record OrderRecord(OrderId orderId, String status, boolean shipped) {
}

record CancellationEligibility(boolean eligible, String reason) {
}

record CancellationRequestDraft(String requestId, OrderId orderId) {
}

record OperationsApprovalRequest(String approvalId, String requestId) {
}

interface OrderService {
    OrderRecord find(OrderId orderId);
}

interface CancellationPolicyService {
    CancellationEligibility check(OrderRecord order);
}

interface CancellationRequestService {
    CancellationRequestDraft createDraft(OrderRecord order, CancellationEligibility eligibility);

    void voidDraft(String requestId);
}

interface OperationsApprovalService {
    OperationsApprovalRequest requestApproval(CancellationRequestDraft draft);
}
