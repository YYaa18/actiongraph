# Java 8 Legacy Client Example

This example is a copy-paste starting point for Java 8 applications that call a deployed ActionGraph Runtime API over HTTP.

It intentionally uses only:

- `actiongraph-control-plane-api`
- Java 8 language features
- JDK HTTP primitives through `ActionGraphRuntimeHttpClient`

The repository test suite compiles `src/main/java/com/company/legacy/LegacyActionGraphClientUsage.java` with `javac --release 8`, so this example is kept in sync with the published Java 8 client API.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-api")
}
```

```bash
export ACTIONGRAPH_RUNTIME_URL=https://agent.example.com/actiongraph/runtime
export ACTIONGRAPH_RUNTIME_TOKEN=runtime-shared-secret
```

