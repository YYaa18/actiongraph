# Java 8 Human Review Client Example

This example is a copy-paste starting point for Java 8 approval portals, enterprise gateways, and callback adapters that call deployed ActionGraph human-review endpoints over HTTP.

It intentionally uses only:

- `actiongraph-control-plane-api`
- Java 8 language features
- JDK HTTP primitives through `ActionGraphHumanReviewHttpClient`

The repository test suite compiles `src/main/java/com/company/approval/ActionGraphHumanReviewClientUsage.java` with `javac --release 8`, so this example is kept in sync with the published Java 8 client API.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-api")
}
```

```bash
export ACTIONGRAPH_REVIEW_TASKS_URL=https://agent.example.com/actiongraph/human-review/tasks
export ACTIONGRAPH_REVIEW_CALLBACK_URL=https://agent.example.com/actiongraph/human-review/callbacks
export ACTIONGRAPH_REVIEW_TOKEN=review-shared-secret
export ACTIONGRAPH_SOURCE_SYSTEM=legacy-approval
export ACTIONGRAPH_REQUEST_ID=REQ-REVIEW-20260611-0001
```
