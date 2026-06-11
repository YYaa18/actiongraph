# Java 8 Legacy Integration

Many financial systems still run on Java 8. ActionGraph therefore makes Java 8 client integration an explicit supported boundary instead of pretending every module can run inside legacy estates.

Java 8 is the official legacy support target. Systems older than Java 8 should integrate through HTTP via an enterprise gateway, ESB, or sidecar owned by that estate; ActionGraph does not claim Java 6/7 source or binary compatibility.

## Compatibility Levels

| Level | Supported Runtime | Intended Use |
|---|---|---|
| Java 8 component catalog | Java 8+ | Legacy systems, gateways, or deployment checks inspect ActionGraph module metadata through `actiongraph-component-catalog` |
| Java 8 HTTP clients | Java 8+ | Legacy systems call deployed Runtime API, Component Catalog, Human Review, and Console endpoints through `actiongraph-control-plane-api` |
| Java 8 embeddable core | Target, not yet complete | Future narrowed core/annotations/governance packages compiled with `--release 8` |
| Modern service runtime | Java 21+ build today | ActionGraph runtime service, Spring Boot starters, JDBC persistence, console, samples, and CI |
| Older than Java 8 | Not an official ActionGraph target | Use platform gateway, ESB, or a Java 8+ sidecar over HTTP; do not embed jars in-process |

## Current Java 8 Artifact

`actiongraph-control-plane-api` is compiled with `--release 8` and has no runtime dependencies. It includes a lightweight aggregate control-plane HTTP client plus individual Runtime, Component Catalog, Human Review, and Console HTTP clients.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-api")
}
```

```java
ActionGraphRuntimeHttpClient client = ActionGraphRuntimeHttpClient
        .builder("https://agent.example.com/actiongraph/runtime")
        .sharedSecret("runtime-shared-secret")
        .defaultHeader("X-Source-System", "legacy-crm")
        .build();

Map<String, String> requestHeaders = new HashMap<String, String>();
requestHeaders.put("X-Request-Id", "REQ-20260611-0001");

ControlPlaneHttpResponse response = client.start("帮客户 C001 生成续约报价", null, requestHeaders);
if (response.successful()) {
    System.out.println(response.body());
}
```

The client uses only JDK `HttpURLConnection` and returns the raw JSON body. It accepts default HTTP headers for stable enterprise metadata such as source system, tenant, or branch, and per-request headers for transaction metadata such as request id, trace id, or correlation id. This keeps legacy projects free to use their existing JSON library, gateway wrapper, or audit logging rules.

If the deployed Runtime API uses the built-in Spring MVC starter, the default server-side trace-header whitelist records `X-Request-Id`, `X-Correlation-Id`, and `X-Source-System` into `RUN_STARTED` / `RUN_RESUMED` trace events. The same run metadata is also copied into `HumanReviewRequest.attributes` when a high-risk action suspends for external approval, so approval portals can display the legacy transaction id, correlation id, and source system without recomputing them. Deployments can change the whitelist with `actiongraph.runtime.api.trace-headers`; keep it limited to non-sensitive audit and correlation metadata.

When a single ActionGraph service exposes the built-in control-plane endpoints under one `/actiongraph` base path, old systems can configure all HTTP clients once:

```java
ActionGraphControlPlaneHttpClient controlPlane = ActionGraphControlPlaneHttpClient
        .builder("https://agent.example.com/actiongraph")
        .sharedSecret("control-plane-shared-secret")
        .defaultHeader("X-Source-System", "legacy-core")
        .maxGetRetries(2)
        .getRetryBackoffMillis(200)
        .build();

ControlPlaneHttpResponse moduleProfiles =
        controlPlane.catalog().profilesForModule("actiongraph-control-plane-api");
ControlPlaneHttpResponse started =
        controlPlane.runtime().start("帮客户 C001 生成续约报价");
ControlPlaneHttpResponse pendingReviews =
        controlPlane.humanReview().pendingTasks();
ControlPlaneHttpResponse traceExport =
        controlPlane.console().traceJsonl("RUN-1");
```

When enterprise routing exposes each surface separately, use the same facade with split base URLs and per-surface secrets. Unconfigured surfaces stay unavailable, so a project that only calls runtime endpoints can configure only `runtimeApiBaseUrl(...)` and still keep catalog, review, and console disabled.

Optional GET retries are available for transient read-side failures. They apply to component catalog reads, human-review task queries, and Console/audit reads on IO failures or HTTP `429`, `502`, `503`, and `504`. They do not retry Runtime start/resume, review decisions, callbacks, or other POST calls, because those operations can create side effects. Defaults are `maxGetRetries=0` and `getRetryBackoffMillis=0`.

For projects that standardize on `.properties` configuration, the same aggregate client can be built without hard-coded builder chains:

```java
Properties properties = new Properties();
properties.setProperty("actiongraph.control-plane.base-url", "https://agent.example.com/actiongraph");
properties.setProperty("actiongraph.control-plane.shared-secret", "control-plane-shared-secret");
properties.setProperty("actiongraph.control-plane.default-header.X-Source-System", "legacy-core");
properties.setProperty("actiongraph.control-plane.max-get-retries", "2");
properties.setProperty("actiongraph.control-plane.get-retry-backoff-millis", "200");

ActionGraphControlPlaneHttpClient controlPlane =
        ActionGraphControlPlaneHttpClientProperties.build(properties);
```

The properties adapter supports aggregate keys such as `actiongraph.control-plane.base-url`, `actiongraph.control-plane.shared-secret`, `actiongraph.control-plane.token-header`, timeout keys, GET retry keys, and `actiongraph.control-plane.default-header.<Header-Name>`. Split gateways can use per-surface keys such as `actiongraph.control-plane.runtime.base-url`, `actiongraph.control-plane.catalog.base-url`, `actiongraph.control-plane.review.tasks-base-url`, `actiongraph.control-plane.review.callback-base-url`, and `actiongraph.control-plane.console.base-url`, with matching per-surface secret and token-header keys.

The same artifact also includes a Java 8 Component Catalog HTTP client for remote ecosystem discovery:

```java
ActionGraphComponentCatalogHttpClient catalog = ActionGraphComponentCatalogHttpClient
        .builder("https://agent.example.com/actiongraph/components")
        .sharedSecret("catalog-shared-secret")
        .defaultHeader("X-Source-System", "deployment-check")
        .build();

Map<String, String> catalogHeaders = new HashMap<String, String>();
catalogHeaders.put("X-Request-Id", "REQ-CATALOG-0001");

ControlPlaneHttpResponse modules = catalog.modulesByCompatibility("java8-client", catalogHeaders);
if (modules.successful()) {
    System.out.println(modules.body());
}
ControlPlaneHttpResponse profiles = catalog.profilesForModule("actiongraph-control-plane-api", catalogHeaders);
if (profiles.successful()) {
    System.out.println(profiles.body());
}
```

That path is useful when a legacy gateway should query the deployed catalog endpoint without loading the local catalog model classes, or when a deployment check needs to reverse-map an artifact to recommended composition profiles.

Java 8 approval portals or review callback adapters can use the same artifact to call deployed human-review endpoints:

```java
ActionGraphHumanReviewHttpClient review = ActionGraphHumanReviewHttpClient
        .builder("https://agent.example.com/actiongraph/human-review/tasks")
        .callbackApiBaseUrl("https://agent.example.com/actiongraph/human-review/callbacks")
        .sharedSecret("review-shared-secret")
        .defaultHeader("X-Source-System", "legacy-approval")
        .build();

ControlPlaneHttpResponse pending = review.pendingTasks();
ControlPlaneHttpResponse decided = review.decide(
        "RUN-1",
        "claim.approval.request",
        Integer.valueOf(0),
        "APPROVED",
        "checker",
        "Approved in legacy approval portal"
);
```

Java 8 audit jobs, operational consoles, or reporting gateways can also query read-only Console endpoints and export audit evidence:

```java
ActionGraphConsoleHttpClient console = ActionGraphConsoleHttpClient
        .builder("https://agent.example.com/actiongraph/console")
        .sharedSecret("console-shared-secret")
        .defaultHeader("X-Source-System", "legacy-audit")
        .build();

ControlPlaneHttpResponse runs = console.runs(
        Integer.valueOf(50),
        Integer.valueOf(0),
        "COMPLETED",
        Boolean.TRUE
);
ControlPlaneHttpResponse traceJsonl = console.traceJsonl("RUN-1");
```

## Copy-Paste Example

Java 8 client templates live at:

```text
docs/examples/java8-maven-consumer/src/main/java/com/company/legacy/MavenJava8ActionGraphConsumerUsage.java
docs/examples/java8-legacy-client/src/main/java/com/company/legacy/LegacyActionGraphClientUsage.java
docs/examples/java8-control-plane-client/src/main/java/com/company/controlplane/ActionGraphControlPlaneClientUsage.java
docs/examples/java8-catalog-http-client/src/main/java/com/company/deployment/ActionGraphCatalogHttpClientUsage.java
docs/examples/java8-human-review-client/src/main/java/com/company/approval/ActionGraphHumanReviewClientUsage.java
docs/examples/java8-console-client/src/main/java/com/company/audit/ActionGraphConsoleClientUsage.java
```

The test suite compiles the standalone source examples with `javac --release 8`, so those examples are both documentation and compatibility evidence. The root build also runs `verifyJava8MavenConsumer`: it publishes `actiongraph-bom`, `actiongraph-component-catalog`, and `actiongraph-control-plane-api` to Maven Local, then compiles the Maven example with Maven Compiler Plugin `release=8`. Together, these gates prove both source compatibility and real Maven BOM consumption for Java 8 callers. The examples demonstrate aggregate control-plane configuration, properties-based aggregate configuration, split endpoint configuration, GET-only transient retry configuration, runtime start/resume calls, catalog metadata calls, human-review task query/decision/callback calls, console run/trace/audit-export calls, default headers, per-request audit headers, response handling, standard control-plane error-code detection, shared-secret header verification, and mapping an unauthorized token exception to a standard control-plane error response.

## Raw HTTP Gateway Contract Reference

Applications that cannot load the Java 8 client jar should call the same deployed Runtime, Component Catalog, Human Review, and Console APIs over HTTP from an existing gateway, ESB adapter, batch job, or sidecar. This path is a protocol reference, not an official Java-before-8 compatibility target.

A raw HTTP template lives at:

```text
docs/examples/pre-java8-http-gateway/src/main/java/com/company/legacygateway/RawHttpActionGraphGatewayUsage.java
```

The test suite compiles that exact file with `javac --release 8` and an empty classpath, scans the source to keep ActionGraph imports and Java 8 library dependencies out of the template, then invokes the compiled class against a local HTTP server to prove optional audit/tracing headers are actually sent. It demonstrates the same `/interpret`, `/runs`, `/runs/{runId}/resume`, component catalog metadata/module/compatibility/profile, human-review task/decision/callback, and Console run/trace/export contracts with shared-secret header forwarding plus optional audit/tracing headers. This gate keeps the HTTP contract honest without expanding the supported Java matrix below Java 8.

## Machine-Readable Compatibility

`actiongraph-component-catalog` now exposes a `compatibility` label for every public component. The built-in Spring catalog endpoint also supports filtering:

```text
GET /actiongraph/components/compatibility/java8-client
```

Current labels:

| Label | Meaning |
|---|---|
| `no-runtime-code` | Version platform or metadata artifact with no loadable runtime classes |
| `java8-client` | Loadable by Java 8 callers; intended for HTTP/client-side integration rather than embedded execution |
| `java8-runtime` | Reserved for future embeddable Java 8 runtime modules; no built-in module uses this label today |
| `java21-plus` | Requires the modern ActionGraph service runtime side |
| `sample-only` | Demonstration code, not a supported library dependency |

Today, `actiongraph-component-catalog` and `actiongraph-control-plane-api` are `java8-client` modules. They are safe for Java 8 projects to import directly. `actiongraph-control-plane-api` covers the aggregate control-plane facade, properties-based aggregate configuration, GET-only retry knobs, and runtime, component catalog, human-review, and console HTTP clients. `actiongraph-core`, Spring starters, JDBC modules, governance modules, LLM modules, Console, and samples are not Java 8 embeddable artifacts; deploy those on the modern ActionGraph service side and let old applications call them over HTTP.

## Non-Goals

- Java-before-8 source/binary compatibility is not a product target. Those systems should integrate over HTTP through an estate-owned gateway or sidecar.
- Spring Boot 3 starters are not Java 8 artifacts.
- The current core runtime still contains Java 16+ language features and Java 21 bytecode, and is not yet an embeddable Java 8 jar.

## Release Gate

Every public module must keep an explicit compatibility label in the component catalog. Automated tests compare `settings.gradle.kts`, `actiongraph-bom`, and the default component catalog so new modules cannot silently bypass compatibility classification. Any new module requires PRD-level approval and an explicit label before it can be published.

Modules listed in the root `java8CompatibleModules` set also run `verifyJava8Compatibility` during `check`. The task fails the build if a Java 8 artifact:

- resolves any main `runtimeClasspath` dependency
- produces a `.class` file with major version greater than `52`

This keeps the Java 8 client promise enforceable in CI instead of relying on manual `javap` checks.

The control-plane API tests also compile the documented Java 8 client examples with `javac --release 8`. Those sources use the aggregate control-plane facade, runtime HTTP client, component catalog HTTP client, human-review HTTP client, console HTTP client, default audit headers, per-request audit headers, error DTO, shared-secret token verifier, token properties interface, and unauthorized exception. The root `check` task also runs the Maven consumer gate described above, so BOM import and published POM consumption stay covered. The same test suite compiles the raw HTTP gateway contract reference with `javac --release 8` and an empty classpath, scans for ActionGraph imports and Java 8 library dependencies, and invokes the compiled template against a local server for runtime, catalog, review, and console calls. These gates catch public API signatures that would be awkwardly compatible as bytecode but unusable from Java 8 source code, and keep the HTTP fallback aligned without treating Java-before-8 as a supported runtime target.
