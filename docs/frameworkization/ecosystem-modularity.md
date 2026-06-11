# Ecosystem Modularity

ActionGraph keeps the runtime framework, Java 8 client surface, and optional control-plane ecosystem independently adoptable. The module surface is intentionally being consolidated: capabilities are selected by dependency plus property switches, not by a long list of nearly identical starter artifacts.

## Layers

| Layer | Modules | Responsibility |
|---|---|---|
| Version platform | `actiongraph-bom` | Aligns all ActionGraph module versions for mix-and-match adoption |
| Runtime kernel | `actiongraph-core` | Action SPI, annotation action registration, planner, executor, policy, trace, goal interpretation contracts, Blackboard seeders, runtime entry service, structured memory contracts |
| Pure Java adapters | `actiongraph-human-review`, `actiongraph-governance`, `actiongraph-llm-deepseek`, `actiongraph-persistence-jdbc` | Human-review model/services, governance policies, LLM interpretation, provider access, and low-level durable repositories |
| Java 8 client surface | `actiongraph-control-plane-api` | Component metadata, composition profiles, Java 8 HTTP clients, shared response DTOs, shared-secret token verification |
| Spring ecosystem | `actiongraph-spring-boot-starter` | Main Spring integration, opt-in runtime/catalog/review/console endpoints, JDBC/memory/human-review/governance wiring, and optional Console UI/API/export endpoints |
| Console services | `actiongraph-console` | Read-only run query service, JDBC read model, CSV/JSONL audit export |
| Samples | `actiongraph-samples` | Reference domains and batch demos; not published as a library |

## Compatibility Labels

The component catalog exposes a machine-readable `compatibility` label for each module so old Java estates can tell which artifacts are safe to load directly.

| Label | Current Use |
|---|---|
| `no-runtime-code` | `actiongraph-bom` |
| `java8-client` | `actiongraph-control-plane-api` |
| `java8-runtime` | Reserved for a future embeddable Java 8 runtime slice |
| `java21-plus` | Current runtime, framework, infrastructure, governance, provider, and Spring ecosystem modules |
| `sample-only` | `actiongraph-samples` |

This is intentionally stricter than a vague "supports Java" claim: Java 8 financial systems should use the deployed Runtime API over HTTP today. In-process runtime embedding remains a separate compatibility refactor.

The module catalog is checked against `settings.gradle.kts`, the module governance ledger, the BOM, and each module's direct Gradle project dependencies in tests. Catalog `requires`, `optionalWith`, and composition profile references must point to real catalog modules. Spring Boot starters are also contract-tested: auto-configuration imports must match annotated auto-configuration classes, starter-owned configuration properties must be enabled, and every web auto-configuration must stay behind an explicit property gate. A new module must be cataloged, classified, approved in `docs/frameworkization/module-governance.md`, and either included in the BOM or explicitly treated as sample-only.

## Composition Rules

- Consumers should import `actiongraph-bom` first, then choose the modules they need without repeating versions.
- A pure Java service can depend only on `actiongraph-core` for execution, annotation-based action registration, GoalCatalog metadata, Blackboard seeding, structured memory, and the reusable runtime entry service.
- Structured long-term memory is part of `actiongraph-core`.
- Repository-backed external review tasks, callback handling, and stable task query/decision projections add `actiongraph-human-review`.
- Non-Spring masking, amount-limit, rule-based permission, review-attribute, and approval-routing policies add `actiongraph-governance`.
- Durable non-Spring/manual runtimes add `actiongraph-persistence-jdbc`, which provides trace, suspended-run, trace read-model, memory, and human-review repositories.
- Provider-neutral natural-language goal interpretation and DeepSeek-compatible model access add `actiongraph-llm-deepseek`.
- Java 8 gateways, approval portals, audit consoles, and legacy systems add `actiongraph-control-plane-api`.
- Spring Boot business services add `actiongraph-spring-boot-starter`. It provides runtime defaults, annotation scanning, structured memory defaults, repository-backed human review, governance wiring, JDBC wiring, and runtime/catalog/review/console HTTP endpoints behind property gates.
- Spring MVC operational monitoring enables `actiongraph.console.enabled=true` in the main starter, which exposes Console API, page, export endpoints, and optional JDBC repository auto-configuration behind property gates.
- A single-deployment control plane should usually use `actiongraph-spring-boot-starter`, then enable only the surfaces it owns.

## Boundaries

`actiongraph-core` is the public runtime kernel. It must remain free of Spring, JDBC drivers, LLM providers, and sample-domain dependencies.

`actiongraph-control-plane-api` is a Java 8 compatible ecosystem utility. It exposes component metadata, composition profiles, response DTO contracts, aggregate and split HTTP clients, properties-based aggregate configuration, safe GET retries, and shared-secret token verification. It must not depend on Spring, runtime repositories, LLM providers, governance, endpoint modules, or third-party HTTP/JSON libraries.

`actiongraph-spring-boot-starter` is the main Spring integration surface. It may auto-configure runtime beans, memory, repository-backed human review, governance policies, JDBC repositories, opt-in generic LLM clients, and runtime/catalog/review/console HTTP endpoints, but every endpoint must remain opt-in through its own `enabled` property. It must not create business actions or domain-specific interpreters, and it creates an `LlmClient` only when `actiongraph.llm.provider` is explicitly configured.

`actiongraph-console` is a reusable read-only monitoring library. It must not depend on Spring Web, expose HTTP endpoints, or mutate runtime state.

Console Spring endpoints live in the main starter. They can create `ActionGraphConsoleService`, auto-configure a JDBC-backed `ConsoleRunRepository` from a `DataSource`, serve the built-in page, and expose read-only JSON/export endpoints behind `actiongraph.console.*` gates. They must not execute, resume, approve, deny, or compensate runs.

There is no separate control-plane aggregate starter. Control-plane behavior is composed from the main Spring starter and explicit property gates.
