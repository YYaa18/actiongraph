# Java 8 Console Client Example

This example is a copy-paste starting point for Java 8 operational consoles, audit gateways, and reporting jobs that call deployed ActionGraph Console endpoints over HTTP.

It intentionally uses only:

- `actiongraph-control-plane-api`
- Java 8 language features
- JDK HTTP primitives through `ActionGraphConsoleHttpClient`

The repository test suite compiles `src/main/java/com/company/audit/ActionGraphConsoleClientUsage.java` with `javac --release 8`, so this example is kept in sync with the published Java 8 client API.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.2.0-SNAPSHOT"))
    implementation("com.actiongraph:actiongraph-control-plane-api")
}
```

```bash
export ACTIONGRAPH_CONSOLE_URL=https://agent.example.com/actiongraph/console
export ACTIONGRAPH_CONSOLE_TOKEN=console-shared-secret
export ACTIONGRAPH_SOURCE_SYSTEM=legacy-audit
export ACTIONGRAPH_REQUEST_ID=REQ-CONSOLE-20260611-0001
```
