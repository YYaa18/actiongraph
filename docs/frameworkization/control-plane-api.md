# Control-Plane API Contracts

`actiongraph-control-plane-api` is a small Java 8 compatible component for shared response contracts, properties-based aggregate configuration, safe GET retries, lightweight aggregate and split control-plane HTTP clients, and shared-secret token verification.

It exists so built-in Spring MVC endpoint starters, custom gateways, CLIs, legacy Java 8 applications, and enterprise control-plane services can speak the same minimal protocol without depending on Spring Web or third-party JSON libraries.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-api")
}
```

The module has no Spring, JDBC, LLM, runtime, servlet, Jackson, OkHttp, or Apache HTTP Client dependency. Its main classes are compiled with `--release 8` so Java 8 applications can load the jar.

CI also compiles the documented Java 8 consumer examples with `javac --release 8` against this module. The compiled examples cover the aggregate control-plane HTTP client, properties-based aggregate configuration, GET-only retry configuration, runtime HTTP client, component catalog HTTP client, human-review HTTP client, console HTTP client, response DTOs, shared-secret token verification, and exception type:

```text
docs/examples/java8-legacy-client/src/main/java/com/company/legacy/LegacyActionGraphClientUsage.java
docs/examples/java8-catalog-http-client/src/main/java/com/company/deployment/ActionGraphCatalogHttpClientUsage.java
docs/examples/java8-control-plane-client/src/main/java/com/company/controlplane/ActionGraphControlPlaneClientUsage.java
docs/examples/java8-human-review-client/src/main/java/com/company/approval/ActionGraphHumanReviewClientUsage.java
docs/examples/java8-console-client/src/main/java/com/company/audit/ActionGraphConsoleClientUsage.java
```

The root build also compiles a real Maven Java 8 consumer after publishing the BOM and Java 8 client artifacts to Maven Local:

```text
docs/examples/java8-maven-consumer
```

Java 8 applications, enterprise gateways, ESB adapters, sidecars, and non-Java callers can speak the deployed ActionGraph HTTP contract directly when they do not want to load the Java 8 client jar. The repository keeps a raw HTTP contract reference that imports no ActionGraph classes and is compiled in CI with `javac --release 8` and an empty classpath; Java 6/7 source or binary compatibility is not an official support target:

```text
docs/examples/raw-http-gateway-contract/src/main/java/com/company/legacygateway/RawHttpActionGraphGatewayUsage.java
```

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

`ControlPlaneHttpResponse` still returns the raw body so Java 8 callers can keep their existing JSON stack, but it also exposes a zero-dependency helper for the standard error shape:

```java
ControlPlaneHttpResponse response = client.resume("RUN-1");
if (response.hasError(ControlPlaneErrorResponse.NOT_CLAIMABLE)) {
    // The suspended run was already claimed by another callback or retry.
    return;
}
if (!response.successful()) {
    throw new IllegalStateException(response.body());
}
```

For non-error payloads such as Console CSV/JSONL exports or successful run responses, `response.error()` returns an empty string.

## Java 8 Aggregate Control-Plane HTTP Client

Legacy applications that call a deployed aggregate control plane can configure all built-in surfaces once through `ActionGraphControlPlaneHttpClient`:

```java
ActionGraphControlPlaneHttpClient client = ActionGraphControlPlaneHttpClient
        .builder("https://agent.example.com/actiongraph")
        .sharedSecret(System.getenv("ACTIONGRAPH_CONTROL_PLANE_TOKEN"))
        .defaultHeader("X-Source-System", "legacy-core")
        .maxGetRetries(2)
        .getRetryBackoffMillis(200)
        .build();

Map<String, String> requestHeaders = new HashMap<String, String>();
requestHeaders.put("X-Request-Id", requestId);

ControlPlaneHttpResponse modules = client.catalog().profilesForModule(
        "actiongraph-control-plane-api", requestHeaders);
ControlPlaneHttpResponse run = client.runtime().start(
        "帮客户 C001 生成续约报价", null, requestHeaders);
ControlPlaneHttpResponse pending = client.humanReview().pendingTasks(requestHeaders);
ControlPlaneHttpResponse trace = client.console().traceJsonl("RUN-1", requestHeaders);
```

The aggregate base URL derives these built-in paths:

- Runtime: `/runtime`
- Component Catalog: `/components`
- Human Review tasks: `/human-review/tasks`
- Human Review callbacks: `/human-review/callbacks`
- Console: `/console`

When enterprise routing exposes each surface through a different gateway, use the same facade with split URLs:

```java
ActionGraphControlPlaneHttpClient client = ActionGraphControlPlaneHttpClient
        .builder()
        .runtimeApiBaseUrl("https://runtime-gw.example.com/actiongraph/runtime")
        .catalogApiBaseUrl("https://catalog-gw.example.com/actiongraph/components")
        .reviewTaskApiBaseUrl("https://review-gw.example.com/actiongraph/human-review/tasks")
        .reviewCallbackApiBaseUrl("https://review-gw.example.com/actiongraph/human-review/callbacks")
        .consoleApiBaseUrl("https://audit-gw.example.com/actiongraph/console")
        .runtimeSharedSecret(System.getenv("ACTIONGRAPH_RUNTIME_TOKEN"))
        .catalogSharedSecret(System.getenv("ACTIONGRAPH_CATALOG_TOKEN"))
        .reviewSharedSecret(System.getenv("ACTIONGRAPH_REVIEW_TOKEN"))
        .consoleSharedSecret(System.getenv("ACTIONGRAPH_CONSOLE_TOKEN"))
        .build();
```

Unconfigured surfaces stay unavailable: `hasCatalog()` / `hasHumanReview()` / `hasConsole()` report what was configured, and the corresponding getter fails fast if a legacy application calls a surface that its gateway did not enable. This keeps the control layer composable while still giving old systems a single client object when that is convenient.

`maxGetRetries` is intentionally GET-only. It retries transient IO failures and HTTP `429`, `502`, `503`, or `504` responses for component catalog reads, human-review task queries, and Console/audit reads. It does not retry Runtime start/resume, review decision, callback, or other POST operations because those calls can create side effects. The default is `0`, so retry behavior is opt-in.

## Java 8 Properties Configuration

Traditional Java 8 applications often receive configuration through `.properties` files, configuration centers, or database-backed gateway tables. `ActionGraphControlPlaneHttpClientProperties` translates those string keys into the same aggregate client without adding Spring Boot or a third-party configuration library:

```java
Properties properties = new Properties();
properties.setProperty("actiongraph.control-plane.base-url", "https://agent.example.com/actiongraph");
properties.setProperty("actiongraph.control-plane.shared-secret", "control-plane-shared-secret");
properties.setProperty("actiongraph.control-plane.default-header.X-Source-System", "legacy-core");
properties.setProperty("actiongraph.control-plane.connect-timeout-millis", "5000");
properties.setProperty("actiongraph.control-plane.read-timeout-millis", "30000");
properties.setProperty("actiongraph.control-plane.max-get-retries", "2");
properties.setProperty("actiongraph.control-plane.get-retry-backoff-millis", "200");

ActionGraphControlPlaneHttpClient client =
        ActionGraphControlPlaneHttpClientProperties.build(properties);
```

Supported aggregate keys:

- `actiongraph.control-plane.base-url`
- `actiongraph.control-plane.shared-secret`
- `actiongraph.control-plane.token-header`
- `actiongraph.control-plane.connect-timeout-millis`
- `actiongraph.control-plane.read-timeout-millis`
- `actiongraph.control-plane.max-get-retries`
- `actiongraph.control-plane.get-retry-backoff-millis`
- `actiongraph.control-plane.default-header.<Header-Name>`

Supported split gateway keys:

- `actiongraph.control-plane.runtime.base-url`
- `actiongraph.control-plane.runtime.shared-secret`
- `actiongraph.control-plane.runtime.token-header`
- `actiongraph.control-plane.catalog.base-url`
- `actiongraph.control-plane.catalog.shared-secret`
- `actiongraph.control-plane.catalog.token-header`
- `actiongraph.control-plane.review.tasks-base-url`
- `actiongraph.control-plane.review.callback-base-url`
- `actiongraph.control-plane.review.shared-secret`
- `actiongraph.control-plane.review.token-header`
- `actiongraph.control-plane.console.base-url`
- `actiongraph.control-plane.console.shared-secret`
- `actiongraph.control-plane.console.token-header`

Blank values are ignored, invalid integer timeouts or retry values fail fast, and unconfigured surfaces keep the same fail-fast behavior as the builder API. Use `ActionGraphControlPlaneHttpClientProperties.build(properties, "legacy.actiongraph")` when a host system requires a custom key prefix.

## Java 8 Runtime HTTP Client

Legacy applications that cannot embed the runtime can call a separately deployed Runtime API through `ActionGraphRuntimeHttpClient`:

```java
ActionGraphRuntimeHttpClient client = ActionGraphRuntimeHttpClient
        .builder("https://agent.example.com/actiongraph/runtime")
        .sharedSecret(System.getenv("ACTIONGRAPH_RUNTIME_TOKEN"))
        .defaultHeader("X-Source-System", "legacy-crm")
        .build();

Map<String, String> requestHeaders = new HashMap<String, String>();
requestHeaders.put("X-Request-Id", requestId);

ControlPlaneHttpResponse response = client.start("帮客户 C001 生成续约报价", null, requestHeaders);
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

`defaultHeader` / `defaultHeaders` are intended for stable enterprise gateway and audit metadata such as source system, tenant, or branch. Each high-level call also accepts per-request headers for transaction-scoped metadata such as request id, trace id, or correlation id. Per-request headers override default headers with the same name. Protocol headers (`Accept`, `Content-Type`) and the configured shared-secret token header are still owned by the client.

When these requests hit the built-in Runtime API Spring MVC starter, only headers configured in `actiongraph.runtime.api.trace-headers` are copied into run trace metadata. The default whitelist captures `X-Request-Id`, `X-Correlation-Id`, and `X-Source-System`. The configured Runtime API token header is never copied into that metadata, even if a deployment accidentally lists it.

For systems that cannot introduce this jar, use the raw HTTP contract shape through an enterprise gateway or sidecar. That path leaves JSON parsing, logging, retries, and network policy under the host system's existing stack; the officially supported client artifact remains Java 8.

## Java 8 Component Catalog HTTP Client

Legacy deployment checks, enterprise gateways, and custom consoles can inspect a deployed Component Catalog endpoint through `ActionGraphComponentCatalogHttpClient`:

```java
ActionGraphComponentCatalogHttpClient catalog = ActionGraphComponentCatalogHttpClient
        .builder("https://agent.example.com/actiongraph/components")
        .sharedSecret(System.getenv("ACTIONGRAPH_CATALOG_TOKEN"))
        .defaultHeader("X-Source-System", "deployment-check")
        .build();

Map<String, String> requestHeaders = new HashMap<String, String>();
requestHeaders.put("X-Request-Id", requestId);

ControlPlaneHttpResponse response = catalog.modulesByCompatibility("java8-client", requestHeaders);
if (!response.successful()) {
    throw new IllegalStateException(response.body());
}
System.out.println(response.body());
```

The client uses only `HttpURLConnection`. It sends:

- `GET /`
- `GET /modules`
- `GET /compatibility/{compatibility}`
- `GET /modules/{module}`
- `GET /modules/{module}/profiles`
- `GET /profiles`
- `GET /profiles/{profile}`

Use `profilesForModule(module)` when a deployment check or gateway needs to ask which composition profiles include a chosen artifact. The response body is raw JSON. This keeps old Java callers from depending on ActionGraph catalog model classes when they only need remote ecosystem discovery or dependency guidance.

The catalog client supports the same default and per-request header API as the runtime client, so deployment probes and custom consoles can pass enterprise tracing and audit headers without adding an HTTP library.

## Java 8 Human Review HTTP Client

Java 8 approval portals, callback adapters, and enterprise gateways can query and decide deployed human-review tasks through `ActionGraphHumanReviewHttpClient`:

```java
ActionGraphHumanReviewHttpClient review = ActionGraphHumanReviewHttpClient
        .builder("https://agent.example.com/actiongraph/human-review/tasks")
        .callbackApiBaseUrl("https://agent.example.com/actiongraph/human-review/callbacks")
        .sharedSecret(System.getenv("ACTIONGRAPH_REVIEW_TOKEN"))
        .defaultHeader("X-Source-System", "legacy-approval")
        .build();

Map<String, String> requestHeaders = new HashMap<String, String>();
requestHeaders.put("X-Request-Id", requestId);

ControlPlaneHttpResponse pending = review.pendingTasks(requestHeaders);
if (!pending.successful()) {
    throw new IllegalStateException(pending.body());
}
System.out.println(pending.body());

ControlPlaneHttpResponse decided = review.decide(
        "RUN-1",
        "claim.approval.request",
        Integer.valueOf(0),
        "APPROVED",
        "checker",
        "Approved in legacy approval portal",
        requestHeaders
);
```

The client uses only `HttpURLConnection`. It sends:

- `GET /pending`
- `GET /runs/{runId}`
- `GET /runs/{runId}/actions/{actionId}`
- `POST /runs/{runId}/actions/{actionId}/decision`
- `POST` to the configured callback endpoint

The default callback URL is derived by replacing a task base URL ending in `/tasks` with `/callbacks`. Set `callbackApiBaseUrl(...)` when enterprise routing exposes task and callback endpoints through different gateways. As with the runtime and catalog clients, response bodies are raw JSON and callers can pass default or per-request audit headers.

## Java 8 Console HTTP Client

Java 8 operational consoles, audit gateways, and reporting jobs can query deployed read-only Console endpoints through `ActionGraphConsoleHttpClient`:

```java
ActionGraphConsoleHttpClient console = ActionGraphConsoleHttpClient
        .builder("https://agent.example.com/actiongraph/console")
        .sharedSecret(System.getenv("ACTIONGRAPH_CONSOLE_TOKEN"))
        .defaultHeader("X-Source-System", "legacy-audit")
        .build();

Map<String, String> requestHeaders = new HashMap<String, String>();
requestHeaders.put("X-Request-Id", requestId);

ControlPlaneHttpResponse runs = console.runs(
        Integer.valueOf(50),
        Integer.valueOf(0),
        "COMPLETED",
        Boolean.TRUE,
        requestHeaders
);
ControlPlaneHttpResponse trace = console.trace("RUN-1", requestHeaders);
ControlPlaneHttpResponse csv = console.traceCsv("RUN-1", requestHeaders);
ControlPlaneHttpResponse jsonl = console.traceJsonl("RUN-1", requestHeaders);
```

The client uses only `HttpURLConnection`. It sends:

- `GET /runs`
- `GET /runs/{runId}`
- `GET /runs/{runId}/trace`
- `GET /runs/export.csv`
- `GET /runs/{runId}/trace/export.csv`
- `GET /runs/{runId}/trace/export.jsonl`

Response bodies are raw JSON, CSV, or JSONL. This lets old Java callers forward audit evidence to existing storage or reporting jobs without pulling in a JSON or HTTP library.

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
