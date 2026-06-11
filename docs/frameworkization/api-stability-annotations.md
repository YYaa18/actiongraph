# API Stability Annotations

ActionGraph defines two lightweight annotations in `actiongraph-core`:

- `com.actiongraph.api.Experimental`
- `com.actiongraph.api.Internal`

They are retained in class files, documented in Javadocs, and have no runtime behavior. They exist to make adoption risk visible before the 1.0 compatibility freeze.

## `@Experimental`

Use `@Experimental` for public APIs that are useful today but may still change as real deployments validate the shape.

Current experimental areas:

- `com.actiongraph.memory`: structured memory contracts and context loading.
- `com.actiongraph.runtime.api.batch`: application-owned batch interpretation SPI.
- `com.actiongraph.llm`: LLM provider adapter, prompt rendering, and structured-output parsing contracts.

Experimental APIs should still be tested and documented. The marker means source and binary compatibility is not yet promised at the same level as the stable core runtime contracts.

## `@Internal`

Use `@Internal` for public implementation surfaces that must remain visible for framework wiring, generated metadata, or adapter reuse, but are not intended as application extension points.

Current internal examples:

- `DefaultExecutionContext`: runtime implementation of `ExecutionContext`; application code should depend on the interface.
- `ConsolePageRenderer`: built-in console page rendering helper; applications should use console services or their own UI.
- `PersistenceJsonCodec`: JDBC persistence serialization helper; applications should use repository APIs.

## Module Boundary

`actiongraph-control-plane-api` remains independent from `actiongraph-core` so Java 8 estates can use it without loading runtime modules. Do not import `com.actiongraph.api.Experimental` or `com.actiongraph.api.Internal` from `actiongraph-control-plane-api` unless the module boundary is intentionally redesigned and approved through module governance.

## Compatibility

After 1.0:

- promoting an experimental API to stable is compatible when the API shape remains source and binary compatible;
- removing `@Internal` from a type can be compatible when the type becomes documented as an application-facing API;
- adding `@Internal` or `@Experimental` to an already stable API is compatibility-sensitive and must be documented in `CHANGELOG.md`;
- removing or breaking an experimental API before 1.0 is allowed but still requires migration notes.

The component catalog guard test checks that the annotation definitions, experimental package markers, internal implementation markers, and this documentation stay in sync.
