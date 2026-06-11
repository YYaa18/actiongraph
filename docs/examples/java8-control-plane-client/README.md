# Java 8 Control-Plane Client Example

This example is a copy-paste starting point for Java 8 applications that call a deployed ActionGraph aggregate control plane over HTTP.

It intentionally uses only:

- `actiongraph-control-plane-api`
- Java 8 language features
- JDK HTTP primitives through `ActionGraphControlPlaneHttpClient`

The repository test suite compiles `src/main/java/com/company/controlplane/ActionGraphControlPlaneClientUsage.java` with `javac --release 8`, so this example is kept in sync with the published Java 8 client API.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-api")
}
```

Use the aggregate client when one ActionGraph service exposes runtime, component catalog, human-review, and console endpoints under the same `/actiongraph` base path:

```bash
export ACTIONGRAPH_BASE_URL=https://agent.example.com/actiongraph
export ACTIONGRAPH_CONTROL_PLANE_TOKEN=control-plane-shared-secret
export ACTIONGRAPH_SOURCE_SYSTEM=legacy-core
export ACTIONGRAPH_REQUEST_ID=REQ-20260611-0001
```

If enterprise routing exposes each surface through a different gateway, use the split base URL builder methods instead: `runtimeApiBaseUrl`, `catalogApiBaseUrl`, `reviewTaskApiBaseUrl`, `reviewCallbackApiBaseUrl`, and `consoleApiBaseUrl`.
