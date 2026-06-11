# Raw HTTP Gateway Contract Reference

This example is for applications or enterprise gateways that cannot load the Java 8 `actiongraph-control-plane-api` jar in-process.

It intentionally uses only JDK HTTP APIs and returns raw JSON response bodies. The application can copy the class into its own gateway adapter, batch job, ESB plugin, or sidecar and keep using its existing JSON parser and audit logging.

The template also accepts optional extra headers for enterprise audit and tracing metadata such as `X-Source-System`, `X-Request-Id`, branch id, tenant id, or an existing gateway correlation id. The test suite invokes the compiled template against a local HTTP server, so this example is checked for both source compatibility and actual header forwarding.

No ActionGraph dependency is required.

The template covers:

- Runtime interpretation/start/resume calls
- Component Catalog metadata, module, compatibility, profile, and profile reverse-lookup queries
- Human Review pending task query, stage decision, and callback calls
- Console run list/run detail/trace queries
- Console CSV and JSONL audit exports

```bash
export ACTIONGRAPH_RUNTIME_URL=https://agent.example.com/actiongraph/runtime
export ACTIONGRAPH_RUNTIME_TOKEN=runtime-shared-secret
export ACTIONGRAPH_CATALOG_URL=https://agent.example.com/actiongraph/components
export ACTIONGRAPH_CATALOG_TOKEN=catalog-shared-secret
export ACTIONGRAPH_REVIEW_TASKS_URL=https://agent.example.com/actiongraph/human-review/tasks
export ACTIONGRAPH_REVIEW_CALLBACK_URL=https://agent.example.com/actiongraph/human-review/callbacks
export ACTIONGRAPH_REVIEW_TOKEN=review-shared-secret
export ACTIONGRAPH_CONSOLE_URL=https://agent.example.com/actiongraph/console
export ACTIONGRAPH_CONSOLE_TOKEN=console-shared-secret
```

The repository test suite compiles `src/main/java/com/company/legacygateway/RawHttpActionGraphGatewayUsage.java` with `javac --release 8` and an empty classpath, then scans the source to keep ActionGraph imports and Java 8 library dependencies out of the template. This keeps the HTTP contract aligned without expanding ActionGraph's official support target below Java 8. Systems older than Java 8 should keep this logic in an enterprise gateway, ESB, or Java 8+ sidecar owned by that estate.
