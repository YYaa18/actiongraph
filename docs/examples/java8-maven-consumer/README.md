# Java 8 Maven Consumer Example

This example proves that a Java 8 Maven project can import the ActionGraph BOM and consume the Java 8 client artifacts exactly as an enterprise legacy application would.

It intentionally uses:

- Maven BOM import for `com.actiongraph:actiongraph-bom`
- `actiongraph-component-catalog`
- `actiongraph-control-plane-api` aggregate control-plane, runtime, component catalog, human-review, and console HTTP clients with GET-only retry configuration
- Maven Compiler Plugin with `source=1.8` and `target=1.8`, so the example works when Maven itself runs on JDK 8
- no Spring, JDBC, LLM, servlet, JSON, or third-party HTTP dependency

The root build runs `verifyJava8MavenConsumer`, which publishes the BOM and Java 8 artifacts to Maven Local, then executes:

```bash
mvn -q clean compile
```

The Maven build directory is redirected to the repository `build/` directory by the Gradle verification task so the docs example remains clean.
