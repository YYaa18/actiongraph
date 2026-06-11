# Publishing Artifacts

The runtime is split into publishable library modules plus a non-published actiongraph-samples module.

## Published Modules

| Module | Artifact | Purpose |
|---|---|---|
| `actiongraph-bom` | `com.actiongraph:actiongraph-bom:0.1.0` | BOM for aligning ActionGraph module versions |
| `actiongraph-core` | `com.actiongraph:actiongraph-core:0.1.0` | Core action, planning, runtime, policy, trace, goal interpretation, runtime entry, and structured memory APIs |
| `actiongraph-annotations` | `com.actiongraph:actiongraph-annotations:0.1.0` | Optional pure Java annotations and adapter for registering ordinary methods as Actions |
| `actiongraph-component-catalog` | `com.actiongraph:actiongraph-component-catalog:0.1.0` | Java 8 compatible component metadata, compatibility labels, and composition profiles |
| `actiongraph-control-plane-api` | `com.actiongraph:actiongraph-control-plane-api:0.1.0` | Java 8 compatible control-plane contracts, properties-based aggregate configuration, safe GET retries, lightweight aggregate / Runtime / Component Catalog / Human Review / Console HTTP clients, and shared-secret token verification |
| `actiongraph-human-review` | `com.actiongraph:actiongraph-human-review:0.1.0` | Optional repository-backed human review tasks, callback handler, approval-chain support, and task query/decision service |
| `actiongraph-governance` | `com.actiongraph:actiongraph-governance:0.1.0` | Optional non-Spring governance policies for masking, amount limits, and rule-based permissions |
| `actiongraph-llm` | `com.actiongraph:actiongraph-llm:0.1.0` | Provider-neutral LLM goal interpretation, prompt rendering, and structured output parsing |
| `actiongraph-llm-deepseek` | `com.actiongraph:actiongraph-llm-deepseek:0.1.0` | Optional DeepSeek-compatible LLM client |
| `actiongraph-persistence-jdbc` | `com.actiongraph:actiongraph-persistence-jdbc:0.1.0` | JDBC trace, suspended-run, trace read-model, structured-memory, and human-review repositories |
| `actiongraph-spring-boot-starter` | `com.actiongraph:actiongraph-spring-boot-starter:0.1.0` | Main Spring Boot integration: annotation scanning, runtime defaults, JDBC repositories, structured memory, repository-backed human review, governance, and opt-in runtime/catalog/review HTTP endpoints |
| `actiongraph-console` | `com.actiongraph:actiongraph-console:0.1.0` | Reusable read-only Console query service, JDBC read model, and CSV/JSONL audit export service |
| `actiongraph-console-spring-boot-starter` | `com.actiongraph:actiongraph-console-spring-boot-starter:0.1.0` | Optional Spring MVC Console API, UI, export endpoints, and JDBC repository auto-configuration |

`actiongraph-samples` remains an application/sample module and is intentionally not published.

Each published library module emits:

- main jar
- sources jar
- javadoc jar
- Maven POM
- Gradle module metadata

`actiongraph-bom` is a Java Platform publication. It emits a Maven BOM POM and Gradle module metadata, but no runtime jar.

## Local Verification

```bash
./gradlew publishToMavenLocal
```

Java 8 client compatibility includes a Maven consumption gate:

```bash
./gradlew verifyJava8MavenConsumer
```

That task publishes `actiongraph-bom`, `actiongraph-component-catalog`, and `actiongraph-control-plane-api` to Maven Local, then compiles `docs/examples/java8-maven-consumer` with Maven Compiler Plugin `source=1.8` / `target=1.8`. The CI workflow also compiles the same consumer after switching to a real JDK 8, proving that a legacy Maven application can import the BOM and omit individual ActionGraph versions while consuming the Java 8 client artifacts.

GitHub Actions also runs the CI workflow on every push to `main` and every pull request:

```bash
./gradlew build --no-daemon --stacktrace
```

The workflow uses Java 21, validates the Gradle wrapper through `gradle/actions/setup-gradle`, and does not set `DEEPSEEK_API_KEY`, so the real LLM smoke test remains gated/skipped in CI.

For real LLM verification, use the manual `DeepSeek Smoke` workflow after configuring the `DEEPSEEK_API_KEY` repository secret. See [Real LLM Smoke Test](llm-smoke.md).

Then a consuming project can depend on:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
}
```

Pure Java/non-Spring consumers can depend on:

```kotlin
implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
implementation("com.actiongraph:actiongraph-core")
```

Pure Java annotation-based action registration adds:

```kotlin
implementation("com.actiongraph:actiongraph-annotations")
```

Structured memory context is part of `actiongraph-core`; Spring Boot structured memory defaults are provided by `actiongraph-spring-boot-starter`.

Goal interpretation contracts and runtime entry services add:

```kotlin
implementation("com.actiongraph:actiongraph-core")
```

Component catalog metadata adds:

```kotlin
implementation("com.actiongraph:actiongraph-component-catalog")
```

Control-plane response contracts, properties-based aggregate configuration, safe GET retries, lightweight aggregate / Runtime / Component Catalog / Human Review / Console HTTP clients, and shared-secret token verification add:

```kotlin
implementation("com.actiongraph:actiongraph-control-plane-api")
```

This artifact is compiled with `--release 8` and has no runtime dependencies, so legacy Java 8 systems can use `ActionGraphControlPlaneHttpClient` to configure all deployed control-plane endpoints from one `/actiongraph` base URL or split gateway URLs, use `ActionGraphControlPlaneHttpClientProperties` to build the aggregate client from `.properties` files or configuration-center keys, opt into GET-only retries for transient catalog/review-task/Console read failures, use `ActionGraphRuntimeHttpClient` to call a deployed Runtime API, use `ActionGraphComponentCatalogHttpClient` to inspect a deployed Component Catalog endpoint, use `ActionGraphHumanReviewHttpClient` to query/decide deployed review tasks and post approval callbacks, use `ActionGraphConsoleHttpClient` to read run/trace status and export CSV/JSONL audit evidence, attach default and per-request enterprise audit/tracing headers, and custom gateways can use `ControlPlaneTokenVerifier` without importing Spring Boot, JDBC, LLM providers, or JSON libraries. The root build runs `verifyJava8Compatibility` for Java 8 compatible modules during `check`; it verifies no main runtime dependencies are resolved and no produced class file exceeds Java 8 bytecode major version `52`. The same `check` path also runs `verifyJava8MavenConsumer`, proving BOM-based Maven consumption of these Java 8 artifacts.

Repository-backed human review adds:

```kotlin
implementation("com.actiongraph:actiongraph-human-review")
```

Provider-neutral LLM-backed goal interpretation adds:

```kotlin
implementation("com.actiongraph:actiongraph-llm")
```

DeepSeek-compatible LLM access adds:

```kotlin
implementation("com.actiongraph:actiongraph-llm-deepseek")
```

Non-Spring governance policies add:

```kotlin
implementation("com.actiongraph:actiongraph-governance")
```

Spring Boot integration, governance auto-configuration, repository-backed human review, durable trace/suspend persistence, structured memory defaults, and runtime/catalog/review HTTP endpoints all use the main starter:

```kotlin
implementation("com.actiongraph:actiongraph-spring-boot-starter")
```

Low-level non-Spring/manual core persistence adds:

```kotlin
implementation("com.actiongraph:actiongraph-persistence-jdbc")
```

The individual endpoint families remain property-gated: `actiongraph.runtime.api.enabled`, `actiongraph.component-catalog.enabled`, `actiongraph.human-review.api.enabled`, and `actiongraph.human-review.callback-endpoint.enabled`.

Custom read-only operational monitoring, JDBC-backed trace read models, and CSV/JSONL audit export add:

```kotlin
implementation("com.actiongraph:actiongraph-console")
```

Spring MVC read-only operational monitoring, built-in page, audit export endpoints, and optional JDBC repository auto-configuration add:

```kotlin
implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
```

Full built-in Spring MVC control-plane endpoints add the main starter plus the optional Console starter:

```kotlin
implementation("com.actiongraph:actiongraph-spring-boot-starter")
implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
```

## Private Repository Publishing

By default, `publish` writes to each module's local `build/repository` directory. To publish to a company Maven repository, pass a URL and optional credentials:

```bash
./gradlew publish \
  -PactionGraphPublishUrl=https://maven.company.internal/releases \
  -PactionGraphPublishUsername="$MAVEN_USERNAME" \
  -PactionGraphPublishPassword="$MAVEN_PASSWORD"
```

Credentials can also come from environment variables:

```bash
ACTIONGRAPH_PUBLISH_USERNAME=... \
ACTIONGRAPH_PUBLISH_PASSWORD=... \
./gradlew publish -PactionGraphPublishUrl=https://maven.company.internal/releases
```

## Versioning

The current version is defined once in the root `build.gradle.kts`:

```kotlin
version = "0.1.0"
```

Before publishing a breaking API change, bump this version and document the migration in the relevant frameworkization note.
