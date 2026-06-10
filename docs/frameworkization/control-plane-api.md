# Control-Plane API Contracts

`actiongraph-control-plane-api` is a small Java 8 compatible component for shared response contracts and lightweight control-plane HTTP clients.

It exists so built-in Spring MVC endpoint starters, custom gateways, CLIs, legacy Java 8 applications, and enterprise control-plane services can speak the same minimal protocol without depending on Spring Web or third-party JSON libraries.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-api")
}
```

The module has no Spring, JDBC, LLM, runtime, servlet, auth, Jackson, OkHttp, or Apache HTTP Client dependency. Its main classes are compiled with `--release 8` so Java 8 applications can load the jar.

## Error Response

All built-in JSON endpoints expose errors with this shape:

```json
{
  "error": "UNAUTHORIZED",
  "message": "Runtime API token is missing or invalid"
}
```

Custom adapters can reuse the same DTO:

```java
return ControlPlaneErrorResponse.notFound("Run not found: RUN-1");
```

Standard factories are provided for the current built-in endpoint codes:

- `badRequest(message)` -> `BAD_REQUEST`
- `conflict(message)` -> `CONFLICT`
- `notClaimable(message)` -> `NOT_CLAIMABLE`
- `notFound(message)` -> `NOT_FOUND`
- `unauthorized(message)` -> `UNAUTHORIZED`

## Java 8 Runtime HTTP Client

Legacy applications that cannot embed the runtime can call a separately deployed Runtime API through `ActionGraphRuntimeHttpClient`:

```java
ActionGraphRuntimeHttpClient client = ActionGraphRuntimeHttpClient
        .builder("https://agent.example.com/actiongraph/runtime")
        .sharedSecret(System.getenv("ACTIONGRAPH_RUNTIME_TOKEN"))
        .build();

ControlPlaneHttpResponse response = client.start("帮客户 C001 生成续约报价");
if (!response.successful()) {
    throw new IllegalStateException(response.body());
}
System.out.println(response.body());
```

The client uses only `HttpURLConnection`. It sends:

- `POST /interpret`
- `POST /runs`
- `POST /runs/{runId}/resume`

The response body is returned as raw JSON so Java 8 callers can parse it with their existing stack, or simply forward it through an enterprise gateway without adding a new JSON dependency.

## Built-In Reuse

These Spring MVC endpoint starters depend on this module transitively:

- `actiongraph-runtime-api-spring-boot-starter`
- `actiongraph-component-catalog-spring-boot-starter`
- `actiongraph-human-review-api-spring-boot-starter`
- `actiongraph-human-review-callback-spring-boot-starter`
- `actiongraph-console-spring-boot-starter`

The aggregate `actiongraph-control-plane-spring-boot-starter` brings those endpoint starters together, so it also receives the shared response contract transitively.

## Boundary

This component is only a protocol contract and lightweight HTTP caller. It does not map exceptions, register Spring advice, verify inbound tokens, execute runs, query repositories, or define enterprise gateway policy.

Endpoint adapters remain responsible for choosing which exceptions map to which HTTP status codes. Pair this module with `actiongraph-control-plane-auth` when the adapter also wants ActionGraph's shared-secret token semantics.
