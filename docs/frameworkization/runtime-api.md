# Runtime API

**Layer: Golden Path / Control-plane Adapter**

Application code should use the root `ActionGraph` facade. This document covers
the optional runtime API adapter for applications that expose ActionGraph runs
through a gateway, worker, custom controller, scheduler, MQ consumer, or batch
process without rewriting the same DTO and metadata handling.

The adapter delegates to the same facade and composes:

- `GoalInterpreter`
- `GoalBlackboardSeederRegistry`
- `GoapExecutor`
- `ActionRegistry`

It does not provide an LLM provider, create repositories, register actions, or force an HTTP endpoint shape.

## Pure Java Adapter Usage

```java
ActionGraphRuntimeOperations runtime = new ActionGraphRuntimeApiService(
        goalInterpreter,
        seederRegistry,
        goapExecutor,
        actionRegistry
);

RuntimeInterpretationResponse interpretation = runtime.interpret(
        "Prepare renewal quote for C123"
);

RuntimeStartResponse started = runtime.start(
        "Prepare renewal quote for C123",
        Map.of(),
        Map.of(
                "requestHeader.X-Request-Id", "REQ-20260611-0001",
                "requestHeader.X-Source-System", "legacy-crm"
        )
);

RuntimeRunResponse resumed = runtime.resume(
        started.run().orElseThrow().runId(),
        Map.of("requestHeader.X-Request-Id", "REQ-20260611-0002")
);
```

`ActionGraphRuntimeApiService` is the default `ActionGraphRuntimeOperations`
adapter. Business code should inject `ActionGraph`; custom controllers, Dubbo
facades, MQ consumers, schedulers, batch workers, or gateway adapters can keep
using this DTO-oriented interface. Production systems should not invoke Gradle
or sample CLI commands; samples are executable documentation only.

`start` returns `CLARIFICATION_REQUIRED` when the interpreter needs more parameters and does not execute any business Action in that branch. When the interpretation is ready, the service seeds a fresh Blackboard and runs the supplied `GoapExecutor` until the run reaches a terminal status or suspends.

The metadata overloads write caller-provided request metadata into `RUN_STARTED` and `RUN_RESUMED` trace events. When a high-risk action requires human review, the same metadata is merged into `HumanReviewRequest.attributes` before the review task is stored, with review-specific attributes taking precedence on key collisions. These values go through the executor's configured `DataMaskingPolicy` and are included in the trace hash chain. Use this for gateway request ids, source system ids, tenant ids, or correlation ids when a custom controller or worker owns the HTTP layer.

## Batch and Model Boundary

Batch invocation is business-owned. `actiongraph-core` exposes `BatchGoalInterpreter` for applications or provider modules that want token-efficient grouped interpretation, plus a conservative `PerItemBatchGoalInterpreter` that delegates to `GoalInterpreter` one item at a time. Structured records should bypass LLM interpretation whenever they already contain goal type and known parameters. Free-text batches can be grouped, cached, or chunked by a provider-specific adapter.

Model transport is provider-owned through `LlmClient`. DeepSeek is one implementation; other commercial, private, or local models should provide their own `LlmClient` or `BatchGoalInterpreter` adapter while preserving the contract that models produce goals and parameters, not plans.

See `docs/frameworkization/runtime-invocation-spi.md` for the production invocation guidance.

## Spring MVC Endpoint

Add the main Spring starter only when an application should expose runtime entry endpoints:

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.2.0-SNAPSHOT"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
}
```

Then enable it explicitly:

```yaml
actiongraph:
  runtime:
    api:
      enabled: true
      path: /actiongraph/runtime
      token-header: X-ActionGraph-Runtime-Token
      shared-secret: ${ACTIONGRAPH_RUNTIME_API_SECRET}
      trace-headers:
        - X-Request-Id
        - X-Correlation-Id
        - X-Source-System
```

The endpoint requires `actiongraph.runtime.api.enabled=true`, a servlet web application plus the root `ActionGraph` facade and a `GoalInterpreter` bean. It exposes only:

```text
POST /actiongraph/runtime/interpret
POST /actiongraph/runtime/runs
POST /actiongraph/runtime/runs/{runId}/resume
```

Example request:

```json
{
  "input": "Prepare renewal quote for C123",
  "knownParameters": {
    "customerId": "C123"
  }
}
```

If `shared-secret` is configured, callers must include the configured token header. Missing or invalid tokens return `401 UNAUTHORIZED`.

For `start` and `resume`, the Spring MVC controller copies only configured `trace-headers` into run metadata and stores them in trace data as `requestHeader.<Header-Name>`. The same keys are available to human-review task attributes when a run suspends. The defaults are `X-Request-Id`, `X-Correlation-Id`, and `X-Source-System`. The configured runtime token header is never traced, even if an application accidentally adds it to `trace-headers`; production deployments should still keep this list limited to non-sensitive audit and correlation metadata.

## Boundary

The runtime entry endpoint intentionally does not enable human-review task query/decision endpoints, approval callbacks, read-only Console endpoints, or audit exports. Enable those surfaces separately only when the deployment should own them.
