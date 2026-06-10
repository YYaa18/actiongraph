# Control-Plane API Contracts

`actiongraph-control-plane-api` is a small Java 8 compatible component for shared response contracts, lightweight control-plane HTTP clients, and shared-secret token verification.

It exists so built-in Spring MVC endpoint starters, custom gateways, CLIs, legacy Java 8 applications, and enterprise control-plane services can speak the same minimal protocol without depending on Spring Web or third-party JSON libraries.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-api")
}
```

The module has no Spring, JDBC, LLM, runtime, servlet, Jackson, OkHttp, or Apache HTTP Client dependency. Its main classes are compiled with `--release 8` so Java 8 applications can load the jar.

CI also compiles a standalone Java 8 consumer source with `javac --release 8` against this module. The compiled snippet covers the runtime HTTP client, response DTOs, shared-secret token verification, and exception type.

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

## Shared-Secret Token Verification

Custom gateways and endpoint starters can reuse the same token semantics without depending on Spring Web:

```java
SharedSecretTokenProtection protection =
        new SharedSecretTokenProtection("X-ActionGraph-Runtime-Token", sharedSecret);

new ControlPlaneTokenVerifier().verify(
        protection,
        request.getHeader(protection.tokenHeader()),
        "runtime API token is missing or invalid"
);
```

Configuration-backed code can implement `SharedSecretTokenProperties`:

```java
public final class RuntimeApiProperties implements SharedSecretTokenProperties {
    private String tokenHeader = "X-ActionGraph-Runtime-Token";
    private String sharedSecret = "";

    @Override
    public String getTokenHeader() {
        return tokenHeader;
    }

    @Override
    public String getSharedSecret() {
        return sharedSecret;
    }
}
```

Then endpoint code can defer header lookup until a shared secret is actually enabled:

```java
tokenVerifier.verify(properties, headers::getFirst, "runtime API token is missing or invalid");
```

If `sharedSecret` is blank, verification succeeds and `headers::getFirst` is not called. If `tokenHeader` is blank, construction fails fast. Token comparison uses constant time. This is a lightweight endpoint guard, not enterprise IAM, OAuth, SSO, RBAC, tenant authorization, auditing, rate limiting, or gateway policy.

## Built-In Reuse

These Spring MVC endpoint starters depend on this module transitively:

- `actiongraph-runtime-api-spring-boot-starter`
- `actiongraph-component-catalog-spring-boot-starter`
- `actiongraph-human-review-api-spring-boot-starter`
- `actiongraph-console-spring-boot-starter`

The aggregate `actiongraph-control-plane-spring-boot-starter` brings those endpoint starters together, so it also receives the shared response contract and shared-secret token verification transitively.

## Boundary

This component is only a protocol contract, lightweight HTTP caller, and simple shared-secret endpoint guard. It does not map exceptions, register Spring advice, execute runs, query repositories, or define enterprise gateway policy.

Endpoint adapters remain responsible for choosing which exceptions map to which HTTP status codes. Production services should still put built-in endpoints behind the company's gateway and pair this token check with the deployment's normal identity and authorization controls.
