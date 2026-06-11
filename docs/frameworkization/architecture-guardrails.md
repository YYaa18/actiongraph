# Architecture Guardrails

ActionGraph uses ArchUnit-style source and Gradle guard tests without adding an ArchUnit runtime dependency.

The guardrails live in `ActionGraphComponentCatalogServiceTest` because that test class already owns the component-catalog, module-governance, documentation, public-contract, and release-surface consistency checks.

## Rules

- Published library modules must follow the approved dependency graph:
  - `actiongraph-core` and `actiongraph-control-plane-api` depend on no other ActionGraph module.
  - `actiongraph-human-review` depends only on core.
  - `actiongraph-governance` depends only on core and human-review.
  - `actiongraph-llm-deepseek` depends only on core.
  - `actiongraph-persistence-jdbc` depends only on core and human-review.
  - `actiongraph-console` depends only on core and persistence-jdbc.
  - `actiongraph-spring-boot-starter` is the only aggregate Spring integration layer and may depend on the runtime, control-plane, console, governance, human-review, and JDBC modules.
- Non-Spring library modules must not import `org.springframework.*`.
- Core must not import Spring, Micrometer, JDBC, Jackson, OkHttp, or sample-domain packages.
- Published library modules must not import `com.actiongraph.samples.*`.

## Why

These checks keep ActionGraph usable as a Spring-like ecosystem rather than a demo application:

- teams can choose core-only, Java 8 control-plane-only, JDBC-only, console-only, or full Spring starter composition;
- provider adapters do not leak into the deterministic runtime;
- samples remain executable documentation rather than a hidden dependency;
- module additions and dependency direction changes require an explicit code review and test update.

## Changing The Graph

When a module dependency is intentionally added, update:

- `build.gradle.kts` / the relevant module `build.gradle.kts`;
- `DefaultActionGraphComponentCatalog`;
- `docs/frameworkization/module-governance.md`;
- the architecture guard test dependency map;
- README / website documentation if the composition story changes.
