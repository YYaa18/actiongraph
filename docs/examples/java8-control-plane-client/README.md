# Java 8 Control-Plane Client Example

This example is a copy-paste starting point for Java 8 applications that call a deployed ActionGraph aggregate control plane over HTTP.

It intentionally uses only:

- `actiongraph-control-plane-api`
- Java 8 language features
- JDK HTTP primitives through `ActionGraphControlPlaneHttpClient`

The repository test suite compiles `src/main/java/com/company/controlplane/ActionGraphControlPlaneClientUsage.java` with `javac --release 8`, so this example is kept in sync with the published Java 8 client API.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.2.0-SNAPSHOT"))
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

Traditional Java 8 applications can also build the same aggregate client from `java.util.Properties`, which maps well to `.properties` files, configuration centers, and gateway tables:

```properties
actiongraph.control-plane.base-url=https://agent.example.com/actiongraph
actiongraph.control-plane.shared-secret=control-plane-shared-secret
actiongraph.control-plane.default-header.X-Source-System=legacy-core
actiongraph.control-plane.connect-timeout-millis=5000
actiongraph.control-plane.read-timeout-millis=30000
actiongraph.control-plane.max-get-retries=2
actiongraph.control-plane.get-retry-backoff-millis=200
```

```java
Properties properties = new Properties();
properties.setProperty("actiongraph.control-plane.base-url", "https://agent.example.com/actiongraph");
properties.setProperty("actiongraph.control-plane.shared-secret", "control-plane-shared-secret");
properties.setProperty("actiongraph.control-plane.default-header.X-Source-System", "legacy-core");
properties.setProperty("actiongraph.control-plane.max-get-retries", "2");
properties.setProperty("actiongraph.control-plane.get-retry-backoff-millis", "200");

ActionGraphControlPlaneHttpClient client =
        ActionGraphControlPlaneHttpClientProperties.build(properties);
```

For split gateways, use keys such as `actiongraph.control-plane.runtime.base-url`, `actiongraph.control-plane.catalog.base-url`, `actiongraph.control-plane.review.tasks-base-url`, `actiongraph.control-plane.review.callback-base-url`, and `actiongraph.control-plane.console.base-url`. Per-surface secrets and token header names are available through keys like `actiongraph.control-plane.runtime.shared-secret` and `actiongraph.control-plane.runtime.token-header`.

`max-get-retries` applies only to safe GET-style catalog, review-task query, and console/audit reads. Runtime start/resume, review decisions, and callbacks are POST operations and are not retried automatically because they can create side effects.
