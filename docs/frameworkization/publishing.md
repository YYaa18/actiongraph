# Publishing Artifacts

The runtime is split into publishable library modules plus a non-published actiongraph-samples module.

## Published Modules

| Module | Artifact | Purpose |
|---|---|---|
| `actiongraph-bom` | `com.actiongraph:actiongraph-bom:0.1.0` | BOM for aligning ActionGraph module versions |
| `actiongraph-core` | `com.actiongraph:actiongraph-core:0.1.0` | Core action, planning, runtime, policy, trace, and interpretation APIs |
| `actiongraph-llm-deepseek` | `com.actiongraph:actiongraph-llm-deepseek:0.1.0` | DeepSeek-compatible LLM goal interpretation |
| `actiongraph-persistence-jdbc` | `com.actiongraph:actiongraph-persistence-jdbc:0.1.0` | JDBC trace, suspended-run, review-task, and memory repositories |
| `actiongraph-spring-boot-starter` | `com.actiongraph:actiongraph-spring-boot-starter:0.1.0` | Spring Boot auto-configuration and annotation-driven action registration |
| `actiongraph-human-review-spring-boot-starter` | `com.actiongraph:actiongraph-human-review-spring-boot-starter:0.1.0` | Optional Spring MVC approval callback endpoint |
| `actiongraph-console-spring-boot-starter` | `com.actiongraph:actiongraph-console-spring-boot-starter:0.1.0` | Optional read-only Console UI and Spring MVC query endpoints |

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

LLM-backed goal interpretation adds:

```kotlin
implementation("com.actiongraph:actiongraph-llm-deepseek")
```

Durable trace and suspend/resume persistence adds:

```kotlin
implementation("com.actiongraph:actiongraph-persistence-jdbc")
```

External approval callbacks add:

```kotlin
implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
```

Read-only operational monitoring adds:

```kotlin
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
