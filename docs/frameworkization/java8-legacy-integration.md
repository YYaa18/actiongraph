# Java 8 Legacy Integration

Many financial systems still run on Java 8, and some are older. ActionGraph therefore distinguishes between two integration modes instead of pretending every module can run everywhere.

## Compatibility Levels

| Level | Supported Runtime | Intended Use |
|---|---|---|
| Java 8 HTTP client | Java 8+ | Legacy systems call a deployed ActionGraph Runtime API through `actiongraph-control-plane-api` |
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

Today, `actiongraph-control-plane-api` is the only `java8-client` module. `actiongraph-core`, Spring starters, JDBC modules, governance modules, LLM modules, Console, and samples are not Java 8 embeddable artifacts.

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

The control-plane API tests also compile an independent consumer source file with `javac --release 8`. That source uses the runtime HTTP client, error DTO, shared-secret token verifier, token properties interface, and unauthorized exception. This catches public API signatures that would be awkwardly compatible as bytecode but unusable from Java 8 source code.
