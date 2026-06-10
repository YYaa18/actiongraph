# Pre-Java-8 Raw HTTP Gateway Example

This example is for legacy applications that cannot load any ActionGraph jar in-process.

It intentionally uses only JDK HTTP APIs and returns raw JSON response bodies. The application can copy the class into its own gateway adapter, batch job, ESB plugin, or sidecar and keep using its existing JSON parser and audit logging.

No ActionGraph dependency is required.

```bash
export ACTIONGRAPH_RUNTIME_URL=https://agent.example.com/actiongraph/runtime
export ACTIONGRAPH_RUNTIME_TOKEN=runtime-shared-secret
```

The repository test suite compiles `src/main/java/com/company/legacygateway/RawHttpActionGraphGatewayUsage.java` with `javac --release 8` and an empty classpath, then scans the source to keep ActionGraph imports and Java 8-only conveniences out of the template. The code is written in a Java 6/7-friendly style, but modern CI toolchains no longer provide a reliable Java 6/7 compilation target. Java 6/7 estates should run their own platform compiler check after copying the file, or keep this logic in an enterprise gateway, ESB, or small Java 8+ sidecar.
