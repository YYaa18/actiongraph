# Annotation-Based Action Usage

## Goal

Business teams should not need to implement the runtime `Action` interface for every operation. The framework now provides annotation-based adaptation:

- `@ActionGraphAction`
- `@ActionGraphGuard`
- `@ActionGraphCompensation`
- `AnnotatedActionFactory`

This is pure Java and does not depend on Spring. A future Spring Boot starter can reuse the same annotations and scan container beans.

## Minimal Example

```java
final class OrderCancellationAdapter {
    @ActionGraphAction(
            id = "order.lookup",
            preconditions = "order-cancellation:ORDER_ID_PRESENT",
            effects = "order-cancellation:ORDER_LOADED",
            riskLevel = ActionRiskLevel.READ_ONLY
    )
    OrderRecord lookup(OrderId orderId) {
        return orderService.findOrder(orderId);
    }

    @ActionGraphAction(
            id = "order.cancellation.request.draft",
            preconditions = {
                    "order-cancellation:ORDER_LOADED",
                    "order-cancellation:CANCELLATION_ELIGIBILITY_CHECKED"
            },
            effects = "order-cancellation:CANCELLATION_REQUEST_DRAFTED",
            riskLevel = ActionRiskLevel.MEDIUM
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
}
```

Register actions:

```java
List<Action> actions = AnnotatedActionFactory.actions(new OrderCancellationAdapter());
ActionRegistry registry = AnnotatedActionFactory.registry(new OrderCancellationAdapter());
```

## Conventions

- Method parameters are loaded from `Blackboard` by runtime class.
- A non-void return value is written back into `Blackboard`.
- Action methods must return a concrete class, `void`, or `ActionResult`.
  Interface and abstract return types fail fast because the default write path stores by concrete class.
- Add `@BlackboardValue("name")` to a parameter or action method to read/write a keyed Blackboard value.
- `ActionResult` return values are honored directly.
- Runtime guard methods must return `boolean` or `Boolean`.
- Compensation methods may return `void` or `CompensationResult`.
- Missing compensation inputs are treated as `noop`.
- Conditions in annotations should use namespaced keys, such as `order-cancellation:ORDER_ID_PRESENT`.

## Current Limits

- Annotation scanning is explicit, not classpath-wide.
- Spring auto-configuration lives in `actiongraph-spring-boot-starter`.
- Blackboard supports multiple values per runtime class through `BlackboardKey`; unannotated parameters still use the default/single-value lookup.

These limits are intentional for the first non-invasive SPI pass.
