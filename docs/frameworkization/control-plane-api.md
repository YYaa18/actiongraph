# Control-Plane API Contracts

`actiongraph-control-plane-api` is a small pure Java component for shared response contracts used by ActionGraph control-plane adapters.

It exists so built-in Spring MVC endpoint starters, custom gateways, CLIs, and enterprise control-plane services can speak the same minimal error shape without depending on Spring Web.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-api")
}
```

The module has no Spring, JDBC, LLM, runtime, servlet, or auth dependency.

## Error Response

All built-in JSON endpoints expose errors with this shape:

```json
{
  "error": "UNAUTHORIZED",
  "message": "Runtime API token is missing or invalid"
}
```

Custom adapters can reuse the same record:

```java
return ControlPlaneErrorResponse.notFound("Run not found: RUN-1");
```

Standard factories are provided for the current built-in endpoint codes:

- `badRequest(message)` -> `BAD_REQUEST`
- `conflict(message)` -> `CONFLICT`
- `notClaimable(message)` -> `NOT_CLAIMABLE`
- `notFound(message)` -> `NOT_FOUND`
- `unauthorized(message)` -> `UNAUTHORIZED`

## Built-In Reuse

These Spring MVC endpoint starters depend on this module transitively:

- `actiongraph-runtime-api-spring-boot-starter`
- `actiongraph-component-catalog-spring-boot-starter`
- `actiongraph-human-review-api-spring-boot-starter`
- `actiongraph-human-review-callback-spring-boot-starter`
- `actiongraph-console-api-spring-boot-starter`
- `actiongraph-console-export-spring-boot-starter`

The aggregate `actiongraph-control-plane-spring-boot-starter` brings those endpoint starters together, so it also receives the shared response contract transitively.

## Boundary

This component is only a DTO contract. It does not map exceptions, inspect HTTP status codes, register Spring advice, verify tokens, execute runs, query repositories, or define enterprise gateway policy.

Endpoint adapters remain responsible for choosing which exceptions map to which HTTP status codes. Pair this module with `actiongraph-control-plane-auth` when the adapter also wants ActionGraph's shared-secret token semantics.
