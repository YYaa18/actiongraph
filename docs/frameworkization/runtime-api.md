# Runtime API

`actiongraph-runtime-api` provides a reusable entry service for applications that want to expose ActionGraph runs through a gateway, CLI, worker, or custom controller without rewriting the same orchestration code.

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
        "Prepare renewal quote for C123"
);

RuntimeRunResponse resumed = api.resume(started.run().orElseThrow().runId());
```

`start` returns `CLARIFICATION_REQUIRED` when the interpreter needs more parameters and does not execute any business Action in that branch. When the interpretation is ready, the service seeds a fresh Blackboard and runs the supplied `GoapExecutor` until the run reaches a terminal status or suspends.

## Spring MVC Starter

Add the optional starter only when an application should expose runtime entry endpoints:

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
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

## Boundary

The Runtime API starter is an entry control-plane component. It intentionally does not expose human-review task query/decision endpoints, approval callbacks, read-only Console endpoints, or audit exports. Add those starters separately only when the deployment should own those surfaces.
