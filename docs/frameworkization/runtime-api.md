# Runtime API

`actiongraph-core` provides a reusable entry service for applications that want to expose ActionGraph runs through a gateway, CLI, worker, or custom controller without rewriting the same orchestration code.

It composes:

- `GoalInterpreter`
- `GoalBlackboardSeederRegistry`
- `GoapExecutor`
- `ActionRegistry`

It does not provide an LLM provider, create repositories, register actions, or expose HTTP endpoints.

## Pure Java Usage

```java
ActionGraphRuntimeApiService api = new ActionGraphRuntimeApiService(
        goalInterpreter,
        seederRegistry,
        goapExecutor,
        actionRegistry
);

RuntimeInterpretationResponse interpretation = api.interpret(
        "Prepare renewal quote for C123"
);

RuntimeStartResponse started = api.start(
        "Prepare renewal quote for C123",
        Map.of(),
        Map.of(
                "requestHeader.X-Request-Id", "REQ-20260611-0001",
                "requestHeader.X-Source-System", "legacy-crm"
        )
);

RuntimeRunResponse resumed = api.resume(
        started.run().orElseThrow().runId(),
        Map.of("requestHeader.X-Request-Id", "REQ-20260611-0002")
);
```

`start` returns `CLARIFICATION_REQUIRED` when the interpreter needs more parameters and does not execute any business Action in that branch. When the interpretation is ready, the service seeds a fresh Blackboard and runs the supplied `GoapExecutor` until the run reaches a terminal status or suspends.

The metadata overloads write caller-provided request metadata into `RUN_STARTED` and `RUN_RESUMED` trace events. When a high-risk action requires human review, the same metadata is merged into `HumanReviewRequest.attributes` before the review task is stored, with review-specific attributes taking precedence on key collisions. These values go through the executor's configured `DataMaskingPolicy` and are included in the trace hash chain. Use this for gateway request ids, source system ids, tenant ids, or correlation ids when a custom controller or worker owns the HTTP layer.

## Spring MVC Starter

Add the optional starter only when an application should expose runtime entry endpoints:

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-core")
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-runtime-api-spring-boot-starter")
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

The starter requires a servlet web application plus `GoalInterpreter`, `GoalBlackboardSeederRegistry`, `GoapExecutor`, and `ActionRegistry` beans. It exposes only:

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

The Runtime API starter is an entry control-plane component. It intentionally does not expose human-review task query/decision endpoints, approval callbacks, read-only Console endpoints, or audit exports. Add those starters separately only when the deployment should own those surfaces.
