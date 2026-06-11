# Java 8 Catalog HTTP Client Example

This example is a copy-paste starting point for Java 8 applications, deployment checks, enterprise gateways, and CLIs that inspect a deployed ActionGraph Component Catalog over HTTP.

It intentionally uses only:

- `actiongraph-control-plane-api`
- Java 8 language features
- JDK HTTP primitives through `ActionGraphComponentCatalogHttpClient`
- raw JSON response bodies, so the host system can keep its existing JSON, logging, retry, and gateway stack

The repository test suite compiles `src/main/java/com/company/deployment/ActionGraphCatalogHttpClientUsage.java` with `javac --release 8`, so this example is kept in sync with the published Java 8 control-plane API.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-api")
}
```

```bash
export ACTIONGRAPH_CATALOG_URL=https://agent.example.com/actiongraph/components
export ACTIONGRAPH_CATALOG_TOKEN=catalog-shared-secret
export ACTIONGRAPH_SOURCE_SYSTEM=deployment-check
export ACTIONGRAPH_REQUEST_ID=REQ-CATALOG-0001
```
