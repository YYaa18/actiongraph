# GoalCatalog-Driven Prompt Rendering

## Purpose

Domain code should not hand-write LLM goal interpretation prompts. A domain now registers goal metadata in a `GoalCatalog` from `actiongraph-core`, and the provider-neutral `actiongraph-llm` module renders a consistent prompt.

## Domain Registration

```java
GoalCatalog catalog = new GoalCatalog();
catalog.register(new GoalDefinition(
        new GoalType("requestOrderCancellation"),
        "Request cancellation for an order.",
        OrderCancellationGoals.requestOrderCancellation(),
        List.of(GoalParameterDefinition.required(
                "orderId",
                "Order identifier. Use canonical IDs such as O100.",
                "O100"
        ))
));
```

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

Provider modules such as `actiongraph-llm-deepseek` supply `LlmClient` implementations only; they do not own prompt shape or parser semantics.

## Current Migration State

Renewal uses `RenewalGoalCatalog` and the catalog-driven renderer. The previous `RenewalGoalPromptRenderer` was removed.

Order cancellation currently uses a rule-based interpreter only. It can be moved onto `GoalCatalog` when LLM interpretation is needed for that domain.
