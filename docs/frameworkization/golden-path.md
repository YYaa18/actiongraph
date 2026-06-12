# ActionGraph Golden Path

**Layer: Golden Path**

This page defines the canonical first-use path. It does not remove any existing
SPI. It only gives each style a clear name so new integrations know what to use
first.

## Canonical Entry

Use the root facade:

```java
RunResult result = actionGraph.start("order.cancel", Map.of("orderId", "O100"));

ChatResult chat = actionGraph.chat("帮客户 C001 准备续约报价");

RunResult resumed = actionGraph.resume(runId);
```

`ActionGraph` is intentionally narrow. It starts runs, chats into runs, and
resumes runs. It does not approve human-review tasks, deliver external events,
run crash recovery, export Console data, or render graph previews. Those are
operational surfaces used by approval systems, MQ listeners, schedulers, and
audit tools.

## Four Layers

| Layer | Surface | When to use |
|---|---|---|
| Golden Path | `@ActionGraphAction`, `@ActionGraphGoal`, `@ActionGraphGoalSeeder`, Spring starter scanning, root `ActionGraph` facade | First integration and most application code |
| Packaging | `ActionGraphContribution` | Publishing a domain as a reusable module or library |
| SPI | `Action`, `GoalBlackboardSeeder`, manual `GoalDefinition`, registries, validator, exporter | Framework extensions and advanced integration code |
| Internal | `@Internal` APIs and implementation classes | Not for application code |

## Error Philosophy

`start(goalType, parameters)` is a code-owned entry. Unknown goal types and
missing required parameters are caller mistakes, so it throws
`ActionGraphInputException`.

`chat(input, knownParameters)` is a user-owned entry. Missing parameters are a
normal conversation branch, so it returns `ChatResult.started() == false` and a
clarification question.

## Runtime API Positioning

`ActionGraphRuntimeApiService` is a control-plane HTTP adapter over the same
facade. Application code should inject `ActionGraph`; HTTP controllers, Java 8
gateways, and cross-process adapters can keep using the runtime API DTO shape.

