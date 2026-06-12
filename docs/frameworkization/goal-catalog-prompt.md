# GoalCatalog-Driven Prompt Rendering

## Purpose

Domain code should not hand-write LLM goal interpretation prompts. A domain now registers goal metadata in a `GoalCatalog` from `actiongraph-core`, and the optional `actiongraph-llm-deepseek` module renders a consistent prompt while also supplying the DeepSeek-compatible client.

## Domain Registration

```java
@ActionGraphGoal(
        type = "order.cancel",
        description = "为指定订单发起取消申请并提交运营审批",
        name = "requestOrderCancellation",
        targetConditions = "order:OPS_APPROVAL_REQUESTED",
        seedConditions = "order:ORDER_ID_PRESENT"
)
record OrderCancellationGoal(
        @GoalParameter(description = "要取消的订单编号", example = "O100")
        String orderId
) {
}
```

Spring Boot registers annotated Goal metadata automatically. Non-Spring users can
still build a catalog explicitly from annotations:

```java
GoalCatalog catalog = new GoalCatalog();
AnnotatedGoalFactory.definitions(OrderCancellationGoal.class).forEach(catalog::register);
```

Manual `new GoalDefinition(...)` registration remains available for lower-level
framework adapters, but application teams should prefer annotated schemas.

## LLM Wiring

```java
new LlmGoalInterpreter(
        llmClient,
        new GoalCatalogPromptRenderer(catalog),
        new GoalInterpretationJsonParser(catalog)
);
```

The prompt renderer includes:

- supported goal types
- goal descriptions
- required/optional parameter names
- parameter descriptions and examples
- the expected JSON shape
- missing field behavior

Custom providers should supply `LlmClient` implementations only; they do not own prompt shape or parser semantics.

## Current Migration State

Renewal uses `RenewalGoalCatalog` and the catalog-driven renderer. The previous `RenewalGoalPromptRenderer` was removed.

Order cancellation currently uses a rule-based interpreter only. It can be moved onto `GoalCatalog` when LLM interpretation is needed for that domain.
