# Publishing Artifacts

The runtime is split into publishable library modules plus a non-published actiongraph-samples module.

## Published Modules

| Module | Artifact | Purpose |
|---|---|---|
| `actiongraph-bom` | `com.actiongraph:actiongraph-bom:0.1.0` | BOM for aligning ActionGraph module versions |
| `actiongraph-core` | `com.actiongraph:actiongraph-core:0.1.0` | Core action, planning, runtime, policy, and trace APIs |
| `actiongraph-annotations` | `com.actiongraph:actiongraph-annotations:0.1.0` | Optional pure Java annotations and adapter for registering ordinary methods as Actions |
| `actiongraph-memory` | `com.actiongraph:actiongraph-memory:0.1.0` | Optional structured memory records, repository contract, in-memory implementation, and Blackboard context loader |
| `actiongraph-memory-spring-boot-starter` | `com.actiongraph:actiongraph-memory-spring-boot-starter:0.1.0` | Optional Spring Boot structured memory and JDBC repository auto-configuration |
| `actiongraph-interpretation` | `com.actiongraph:actiongraph-interpretation:0.1.0` | Optional goal interpretation contracts, GoalCatalog metadata, and Blackboard seeders |
| `actiongraph-runtime-api` | `com.actiongraph:actiongraph-runtime-api:0.1.0` | Reusable goal interpretation, start, and resume service |
| `actiongraph-component-catalog` | `com.actiongraph:actiongraph-component-catalog:0.1.0` | Reusable component catalog and composition profile metadata |
| `actiongraph-control-plane-api` | `com.actiongraph:actiongraph-control-plane-api:0.1.0` | Java 8 compatible control-plane contracts, lightweight runtime HTTP client, and shared-secret token verification |
| `actiongraph-human-review` | `com.actiongraph:actiongraph-human-review:0.1.0` | Optional repository-backed human review tasks, callback handler, approval-chain support, and task query/decision service |
| `actiongraph-governance` | `com.actiongraph:actiongraph-governance:0.1.0` | Optional non-Spring governance policies for masking, amount limits, and rule-based permissions |
| `actiongraph-governance-human-review` | `com.actiongraph:actiongraph-governance-human-review:0.1.0` | Optional non-Spring human-review governance policies for amount review attributes and approval routing |
| `actiongraph-llm` | `com.actiongraph:actiongraph-llm:0.1.0` | Provider-neutral LLM goal interpretation, prompt rendering, and structured output parsing |
| `actiongraph-llm-deepseek` | `com.actiongraph:actiongraph-llm-deepseek:0.1.0` | Optional DeepSeek-compatible LLM client |
| `actiongraph-persistence-jdbc` | `com.actiongraph:actiongraph-persistence-jdbc:0.1.0` | Core JDBC trace, suspended-run, and trace read-model repositories |
| `actiongraph-memory-jdbc` | `com.actiongraph:actiongraph-memory-jdbc:0.1.0` | Optional JDBC structured-memory repository |
| `actiongraph-human-review-jdbc` | `com.actiongraph:actiongraph-human-review-jdbc:0.1.0` | Optional JDBC human-review task repository |
| `actiongraph-spring-boot-starter` | `com.actiongraph:actiongraph-spring-boot-starter:0.1.0` | Spring Boot auto-configuration and annotation-driven action registration |
| `actiongraph-governance-spring-boot-starter` | `com.actiongraph:actiongraph-governance-spring-boot-starter:0.1.0` | Optional Spring Boot governance policies for masking, amount limits, and rule-based permissions |
| `actiongraph-governance-human-review-spring-boot-starter` | `com.actiongraph:actiongraph-governance-human-review-spring-boot-starter:0.1.0` | Optional Spring Boot human-review governance policies for amount review attributes and approval routing |
| `actiongraph-jdbc-spring-boot-starter` | `com.actiongraph:actiongraph-jdbc-spring-boot-starter:0.1.0` | Optional Spring Boot auto-configuration for core JDBC repositories |
| `actiongraph-runtime-api-spring-boot-starter` | `com.actiongraph:actiongraph-runtime-api-spring-boot-starter:0.1.0` | Optional Spring MVC runtime start and resume endpoints |
| `actiongraph-component-catalog-spring-boot-starter` | `com.actiongraph:actiongraph-component-catalog-spring-boot-starter:0.1.0` | Optional Spring MVC component catalog endpoints |
| `actiongraph-human-review-spring-boot-starter` | `com.actiongraph:actiongraph-human-review-spring-boot-starter:0.1.0` | Optional Spring Boot repository-backed review policy and JDBC repository auto-configuration |
| `actiongraph-human-review-api-spring-boot-starter` | `com.actiongraph:actiongraph-human-review-api-spring-boot-starter:0.1.0` | Optional Spring MVC human-review task API and approval callback endpoints |
| `actiongraph-console` | `com.actiongraph:actiongraph-console:0.1.0` | Reusable read-only Console query service, JDBC read model, and CSV/JSONL audit export service |
| `actiongraph-console-spring-boot-starter` | `com.actiongraph:actiongraph-console-spring-boot-starter:0.1.0` | Optional Spring MVC Console API, UI, export endpoints, and JDBC repository auto-configuration |
| `actiongraph-control-plane-spring-boot-starter` | `com.actiongraph:actiongraph-control-plane-spring-boot-starter:0.1.0` | Optional aggregate for runtime, component catalog, human-review, callback, and Console endpoint starters |

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

Structured memory context adds:

```kotlin
implementation("com.actiongraph:actiongraph-memory")
```

Spring Boot structured memory auto-configuration adds:

```kotlin
implementation("com.actiongraph:actiongraph-memory-spring-boot-starter")
```

Goal interpretation contracts add:

```kotlin
implementation("com.actiongraph:actiongraph-interpretation")
```

Runtime entry services add:

```kotlin
implementation("com.actiongraph:actiongraph-runtime-api")
```

Component catalog metadata adds:

```kotlin
implementation("com.actiongraph:actiongraph-component-catalog")
```

Control-plane response contracts, lightweight Runtime HTTP client, and shared-secret token verification add:

```kotlin
implementation("com.actiongraph:actiongraph-control-plane-api")
```

This artifact is compiled with `--release 8` and has no runtime dependencies, so legacy Java 8 systems can use `ActionGraphRuntimeHttpClient` to call a deployed Runtime API and custom gateways can use `ControlPlaneTokenVerifier` without importing Spring Boot, JDBC, LLM providers, or JSON libraries.

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

Non-Spring human-review governance policies add:

```kotlin
implementation("com.actiongraph:actiongraph-governance-human-review")
```

Spring Boot governance auto-configuration adds:

```kotlin
implementation("com.actiongraph:actiongraph-governance-spring-boot-starter")
```

Spring Boot human-review governance auto-configuration adds:

```kotlin
implementation("com.actiongraph:actiongraph-governance-human-review-spring-boot-starter")
```

Spring Boot durable trace and suspend/resume persistence adds:

```kotlin
implementation("com.actiongraph:actiongraph-jdbc-spring-boot-starter")
```

Spring Boot structured memory defaults and optional JDBC memory persistence add:

```kotlin
implementation("com.actiongraph:actiongraph-memory-spring-boot-starter")
```

Low-level non-Spring/manual core persistence adds:

```kotlin
implementation("com.actiongraph:actiongraph-persistence-jdbc")
```

Low-level non-Spring/manual optional persistence adds:

```kotlin
implementation("com.actiongraph:actiongraph-memory-jdbc")
implementation("com.actiongraph:actiongraph-human-review-jdbc")
```

Spring Boot repository-backed review and optional JDBC task persistence add:

```kotlin
implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
```

Spring MVC runtime start/resume endpoints add:

```kotlin
implementation("com.actiongraph:actiongraph-runtime-api-spring-boot-starter")
```

Spring MVC component catalog endpoints add:

```kotlin
implementation("com.actiongraph:actiongraph-component-catalog-spring-boot-starter")
```

Spring MVC human-review task and callback endpoints add:

```kotlin
implementation("com.actiongraph:actiongraph-human-review-api-spring-boot-starter")
```

Custom read-only operational monitoring, JDBC-backed trace read models, and CSV/JSONL audit export add:

```kotlin
implementation("com.actiongraph:actiongraph-console")
```

Spring MVC read-only operational monitoring, built-in page, audit export endpoints, and optional JDBC repository auto-configuration add:

```kotlin
implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
```

Full built-in Spring MVC control-plane endpoints add:

```kotlin
implementation("com.actiongraph:actiongraph-control-plane-spring-boot-starter")
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
