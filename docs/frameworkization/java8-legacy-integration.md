# Java 8 Legacy Integration

Many financial systems still run on Java 8, and some are older. ActionGraph therefore distinguishes between two integration modes instead of pretending every module can run everywhere.

## Compatibility Levels

| Level | Supported Runtime | Intended Use |
|---|---|---|
| Java 8 component catalog | Java 8+ | Legacy systems, gateways, or deployment checks inspect ActionGraph module metadata through `actiongraph-component-catalog` |
| Java 8 HTTP clients | Java 8+ | Legacy systems call deployed Runtime API and Component Catalog endpoints through `actiongraph-control-plane-api` |
| Java 8 embeddable core | Target, not yet complete | Future narrowed core/annotations/governance packages compiled with `--release 8` |
| Modern service runtime | Java 21+ build today | ActionGraph runtime service, Spring Boot starters, JDBC persistence, console, samples, and CI |
| Older than Java 8 | HTTP only | Use platform gateway, ESB, or a thin Java 8+ sidecar; do not embed jars in-process |

## Current Java 8 Artifact

`actiongraph-control-plane-api` is compiled with `--release 8` and has no runtime dependencies.

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
        .build();

ControlPlaneHttpResponse response = client.start("帮客户 C001 生成续约报价");
if (response.successful()) {
    System.out.println(response.body());
}
```

The client uses only JDK `HttpURLConnection` and returns the raw JSON body. This keeps legacy projects free to use their existing JSON library, gateway wrapper, or audit logging rules.

The same artifact also includes a Java 8 Component Catalog HTTP client for remote ecosystem discovery:

```java
ActionGraphComponentCatalogHttpClient catalog = ActionGraphComponentCatalogHttpClient
        .builder("https://agent.example.com/actiongraph/components")
        .sharedSecret("catalog-shared-secret")
        .build();

ControlPlaneHttpResponse modules = catalog.modulesByCompatibility("java8-client");
if (modules.successful()) {
    System.out.println(modules.body());
}
```

That path is useful when a legacy gateway should query the deployed catalog endpoint without loading the local catalog model classes.

## Copy-Paste Example

A Java 8 client template lives at:

```text
docs/examples/java8-legacy-client/src/main/java/com/company/legacy/LegacyActionGraphClientUsage.java
docs/examples/java8-catalog-http-client/src/main/java/com/company/deployment/ActionGraphCatalogHttpClientUsage.java
```

The test suite compiles those exact files with `javac --release 8`, so the examples are both documentation and compatibility evidence. They demonstrate runtime start calls, catalog metadata calls, response handling, shared-secret header verification, and mapping an unauthorized token exception to a standard control-plane error response.

## Older-Than-Java-8 HTTP Gateway Example

Java 6/7 applications should not load ActionGraph jars in-process. They can still call the same deployed Runtime API over HTTP from an existing gateway, ESB adapter, batch job, or sidecar.

A raw HTTP template lives at:

```text
docs/examples/pre-java8-http-gateway/src/main/java/com/company/legacygateway/RawHttpActionGraphGatewayUsage.java
```

The test suite compiles that exact file with `javac --release 8` and an empty classpath, then scans the source to keep ActionGraph imports and Java 8-only conveniences out of the template. It demonstrates the same `/interpret`, `/runs`, and `/runs/{runId}/resume` contract with shared-secret header forwarding. This is not a full Java 6/7 bootclasspath check because modern CI toolchains no longer provide reliable Java 6/7 targets; Java 6/7 estates should run their own platform compiler check after copying the file, or reuse the HTTP shape through an enterprise gateway, ESB, or Java 8+ sidecar.

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

Today, `actiongraph-component-catalog` and `actiongraph-control-plane-api` are `java8-client` modules. `actiongraph-core`, Spring starters, JDBC modules, governance modules, LLM modules, Console, and samples are not Java 8 embeddable artifacts.

## Non-Goals

- Java 6/7 in-process embedding is not planned. Those systems should integrate over HTTP or through a sidecar.
- Spring Boot 3 starters are not Java 8 artifacts.
- The current core runtime still contains Java 16+ language features and Java 21 bytecode, and is not yet an embeddable Java 8 jar.

## Release Gate

Every public module must keep an explicit compatibility label in the component catalog. Automated tests compare `settings.gradle.kts`, `actiongraph-bom`, and the default component catalog so new modules cannot silently bypass compatibility classification. Any new module requires PRD-level approval and an explicit label before it can be published.

Modules listed in the root `java8CompatibleModules` set also run `verifyJava8Compatibility` during `check`. The task fails the build if a Java 8 artifact:

- resolves any main `runtimeClasspath` dependency
- produces a `.class` file with major version greater than `52`

This keeps the Java 8 client promise enforceable in CI instead of relying on manual `javap` checks.

The control-plane API tests also compile the documented Java 8 client examples with `javac --release 8`. Those sources use the runtime HTTP client, component catalog HTTP client, error DTO, shared-secret token verifier, token properties interface, and unauthorized exception. The same test suite compiles the raw HTTP gateway example with `javac --release 8` and an empty classpath, then scans for ActionGraph imports and Java 8-only conveniences. These gates catch public API signatures that would be awkwardly compatible as bytecode but unusable from Java 8 source code, and keep the older-than-Java-8 HTTP fallback honest.
