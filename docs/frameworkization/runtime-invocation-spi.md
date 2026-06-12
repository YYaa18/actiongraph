# Runtime Invocation SPI

ActionGraph is a framework ecosystem, not a deployment shape. Production systems should not invoke Gradle tasks or sample CLI commands to start runs. They should own their controller, message consumer, scheduler, batch worker, ESB adapter, or sidecar and compose ActionGraph through stable interfaces.

## Primary Facade

Modern in-process application code should depend on the root facade:

```java
RunResult result = actionGraph.start("order.cancel", Map.of("orderId", "O100"));
ChatResult chat = actionGraph.chat("帮客户 C001 准备续约报价");
RunResult resumed = actionGraph.resume(runId);
```

`ActionGraph` is the golden-path API for application code. It keeps the
business entry surface narrow: start, chat, and resume.

## Control-plane Adapter Contract

`actiongraph-core` exposes `ActionGraphRuntimeOperations`:

```java
public interface ActionGraphRuntimeOperations {
    RuntimeInterpretationResponse interpret(String input);

    RuntimeInterpretationResponse interpret(String input, Map<String, String> knownParameters);

    RuntimeStartResponse start(String input);

    RuntimeStartResponse start(String input, Map<String, String> knownParameters);

    RuntimeStartResponse start(
            String input,
            Map<String, String> knownParameters,
            Map<String, String> runMetadata
    );

    RuntimeStartResponse start(GoalInterpretation interpretation);

    RuntimeStartResponse start(GoalInterpretation interpretation, Map<String, String> runMetadata);

    RuntimeRunResponse resume(String runId);

    RuntimeRunResponse resume(String runId, Map<String, String> runMetadata);
}
```

`ActionGraphRuntimeApiService` is the default adapter implementation over the
root `ActionGraph` facade. Applications can still inject the interface, wrap it
with their own authorization/audit/idempotency layer, or provide another
implementation for tests, gateways, or tenant-specific routing.

The optional Spring MVC runtime endpoints are only one adapter over the same contract. A production service can also write its own Spring controller, Dubbo facade, MQ consumer, scheduled job, or batch worker that calls `ActionGraphRuntimeOperations` directly.

## Legacy Java 8 Systems

Java 8 business systems should not embed the Java 21 runtime modules. The supported shape is:

- run ActionGraph runtime on a modern service side;
- expose only the required opt-in HTTP endpoints or an enterprise gateway;
- call those endpoints from Java 8 code with `actiongraph-control-plane-api`, depending on `ActionGraphRuntimeGateway` and using `ActionGraphRuntimeHttpClient` as the default implementation;
- keep business-specific request validation, transaction ids, and source-system metadata in the legacy service.

This keeps the framework runtime modern while allowing old systems to participate without Spring, third-party dependencies, or bytecode incompatibility.

## Batch Invocation

Batch processing is business-owned. The framework should not decide what a batch id means, how files are read, whether retries are per row or per file, or which approval/reporting process closes the batch.

`actiongraph-core` therefore exposes a small batch interpretation SPI:

```java
public interface BatchGoalInterpreter {
    List<BatchGoalInterpretation> interpret(List<BatchGoalInput> inputs);
}
```

`PerItemBatchGoalInterpreter` is the conservative default adapter. It delegates each item to the existing `GoalInterpreter`, preserving `itemId` so the caller can map results back to source records. Provider modules or applications can implement `BatchGoalInterpreter` when they have a better token strategy.

Recommended token strategy:

- structured source records should bypass LLM interpretation and map directly to `GoalInterpretation` plus `knownParameters`;
- homogeneous free-text records can be grouped into provider-specific batch prompts when the model and context window support it;
- normalized input and known parameters can be cached before calling a model;
- very large files should be chunked by provider context window and retry semantics, not by ActionGraph core;
- deterministic execution stays outside the LLM path, so batching only affects interpretation cost, not planning, guard, compensation, or trace semantics.

## Model Portability

ActionGraph's model boundary is `LlmClient`. Prompt construction and parser semantics stay provider-neutral through `GoalCatalogPromptRenderer` and `GoalInterpretationJsonParser`; DeepSeek is one provider implementation, not a framework dependency.

Other providers should implement `LlmClient` or a higher-level `BatchGoalInterpreter` adapter. JSON mode, max token names, model ids, streaming flags, and vendor-specific response formats belong inside that provider adapter. The core contract remains: LLMs produce goals and parameters, never plans or executable actions.

## Production Boundary

Do not invoke Gradle or sample CLI commands from production. Samples are executable documentation. Production entrypoints should compose the framework contracts:

- `ActionGraph` for modern in-process runtime integration;
- `ActionGraphRuntimeOperations` for custom control-plane adapters;
- `ActionGraphRuntimeGateway` / `ActionGraphRuntimeHttpClient` for Java 8 or out-of-process callers;
- `BatchGoalInterpreter` when the caller owns token-efficient batch interpretation;
- `LlmClient` when a provider module owns model-specific transport.
