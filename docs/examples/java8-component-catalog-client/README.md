# Java 8 Component Catalog Client Example

This example is a copy-paste starting point for Java 8 deployment checks, enterprise gateways, CLIs, and bootstrap scripts that need to inspect ActionGraph's module catalog without starting the runtime.

It intentionally uses only:

- `actiongraph-component-catalog`
- Java 8 language features
- local in-process metadata from `ActionGraphComponentCatalogService`

The repository test suite compiles `src/main/java/com/company/deployment/ActionGraphComponentCatalogUsage.java` with `javac --release 8`, so this example is kept in sync with the published Java 8 catalog API.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-component-catalog")
}
```
