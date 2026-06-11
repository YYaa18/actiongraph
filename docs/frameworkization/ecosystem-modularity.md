# Ecosystem Modularity

ActionGraph is split so the public runtime framework and optional ecosystem/control-plane components can be adopted independently.

## Layers

| Layer | Modules | Responsibility |
|---|---|---|
| Version platform | `actiongraph-bom` | Aligns all ActionGraph module versions for mix-and-match adoption |
| Runtime kernel | `actiongraph-core` | Action SPI, planner, executor, policy, trace |
| Optional adapters | `actiongraph-annotations`, `actiongraph-memory`, `actiongraph-memory-spring-boot-starter`, `actiongraph-memory-jdbc`, `actiongraph-interpretation`, `actiongraph-runtime-api`, `actiongraph-human-review`, `actiongraph-human-review-jdbc`, `actiongraph-human-review-spring-boot-starter`, `actiongraph-spring-boot-starter`, `actiongraph-governance`, `actiongraph-governance-human-review`, `actiongraph-governance-spring-boot-starter`, `actiongraph-governance-human-review-spring-boot-starter`, `actiongraph-jdbc-spring-boot-starter`, `actiongraph-llm`, `actiongraph-llm-deepseek`, `actiongraph-persistence-jdbc` | Pure Java annotation action registration, structured memory context, Spring memory/JDBC wiring, JDBC memory storage, goal interpretation contracts, reusable runtime entry API service, repository-backed human review with task query APIs, JDBC review storage, Spring review-policy/JDBC wiring, Spring runtime wiring, reusable governance policies, human-review governance extensions, Spring governance wiring, Spring core JDBC wiring, provider-neutral LLM goal interpretation, DeepSeek provider adapter, low-level durable repositories |
| Control-plane ecosystem | `actiongraph-component-catalog`, `actiongraph-control-plane-api`, `actiongraph-component-catalog-spring-boot-starter`, `actiongraph-runtime-api-spring-boot-starter`, `actiongraph-human-review-api-spring-boot-starter`, `actiongraph-console`, `actiongraph-console-spring-boot-starter`, `actiongraph-control-plane-spring-boot-starter` | Machine-readable component catalog, shared control-plane response contracts, shared control-plane token verification, optional catalog HTTP endpoints, runtime goal interpretation/start/resume HTTP endpoints, human-review task query/decision and callback HTTP endpoints, read-only Console query/JDBC/export services, optional Spring Console API/UI/export/JDBC auto-configuration, full endpoint aggregate starter |
| Samples | `actiongraph-samples` | Reference domains and batch demos; not published as a library |

## Compatibility Labels

The component catalog exposes a machine-readable `compatibility` label for each module so old Java estates can tell which artifacts are safe to load directly.

| Label | Current Use |
|---|---|
| `no-runtime-code` | `actiongraph-bom` |
| `java8-client` | `actiongraph-component-catalog` for Java 8 local component discovery; `actiongraph-control-plane-api` for Java 8 HTTP/client integration |
| `java8-runtime` | Reserved for a future embeddable Java 8 runtime slice |
| `java21-plus` | Current runtime, framework, infrastructure, governance, provider, and Spring ecosystem modules |
| `sample-only` | `actiongraph-samples` |

This is intentionally stricter than a vague "supports Java" claim: Java 8 and older financial systems should use the deployed Runtime API over HTTP today. In-process runtime embedding remains a separate compatibility refactor.

The module catalog is checked against `settings.gradle.kts` and the BOM in tests. A new module must be cataloged, classified, and either included in the BOM or explicitly treated as sample-only.

## Composition Rules

- Consumers should import `actiongraph-bom` first, then choose the modules they need without repeating versions.
- A pure Java service can depend only on `actiongraph-core`.
- Pure Java annotation-based registration adds `actiongraph-annotations`.
- Structured long-term memory adds `actiongraph-memory`.
- Spring Boot structured memory defaults and JDBC memory wiring add `actiongraph-memory-spring-boot-starter`.
- JDBC structured memory adds `actiongraph-memory-jdbc`; Spring Boot JDBC memory is wired by `actiongraph-memory-spring-boot-starter` when `actiongraph.persistence.jdbc.enabled=true`.
- Goal catalogs, rule-based interpreters, and Goal-to-Blackboard seeding add `actiongraph-interpretation`.
- Stable non-Spring goal interpretation/start/resume entry services add `actiongraph-runtime-api`; Spring MVC runtime entry endpoints add `actiongraph-runtime-api-spring-boot-starter`.
- Machine-readable module and composition metadata adds `actiongraph-component-catalog`; Spring MVC catalog endpoints add `actiongraph-component-catalog-spring-boot-starter`.
- Shared control-plane contracts, Java 8 Runtime API and Component Catalog HTTP clients, and shared-secret token verification add `actiongraph-control-plane-api`; built-in Spring MVC endpoint starters bring it transitively.
- Repository-backed external review tasks, callback handling, and stable task query/decision projections add `actiongraph-human-review`.
- JDBC review task storage adds `actiongraph-human-review-jdbc`; Spring Boot JDBC review task storage is wired by `actiongraph-human-review-spring-boot-starter` when `actiongraph.persistence.jdbc.enabled=true`.
- A Spring Boot business service can depend on `actiongraph-spring-boot-starter` without exposing any HTTP control-plane endpoint.
- Non-Spring masking, amount-limit, and rule-based permission policies add `actiongraph-governance`; human-review approval routing or review attributes add `actiongraph-governance-human-review`.
- Spring Boot masking and amount-limit governance add `actiongraph-governance-spring-boot-starter`; risk-based approval routing and review attributes add `actiongraph-governance-human-review-spring-boot-starter`.
- Durable Spring Boot production runs add `actiongraph-jdbc-spring-boot-starter`; non-Spring/manual runtimes add `actiongraph-persistence-jdbc`. Memory and review-task durability are opt-in through their own JDBC modules.
- Provider-neutral natural-language goal interpretation adds `actiongraph-llm`.
- DeepSeek-compatible model access adds `actiongraph-llm-deepseek`.
- Spring Boot repository-backed approval tasks add `actiongraph-human-review-spring-boot-starter`; Spring MVC approval task APIs and external approval callbacks add `actiongraph-human-review-api-spring-boot-starter`.
- Custom operational monitoring, JDBC-backed read models, and CSV/JSONL audit export add `actiongraph-console`. Spring MVC operational monitoring adds `actiongraph-console-spring-boot-starter`, which exposes Console API, page, export endpoints, and optional JDBC repository auto-configuration behind property gates.
- Single-deployment built-in control planes can use `actiongraph-control-plane-spring-boot-starter` as a convenience aggregate over runtime, component-catalog, human-review, callback, and Console endpoint starters; split starters remain preferred when endpoint exposure must be minimal.

The JDBC Spring Boot starter depends on the low-level core JDBC repositories and the Spring `DataSource` contract. It creates durable trace/suspended-run repository beans only when `actiongraph.persistence.jdbc.enabled=true`, and it does not register actions, execute runs, expose HTTP endpoints, configure Memory/Human Review storage, or start any control-plane surface.

The LLM module provides provider-neutral goal interpretation, GoalCatalog prompt rendering, and structured output parsing. Provider modules such as `actiongraph-llm-deepseek` depend on it and add only transport/model-specific clients.

The Memory module provides structured memory records, the repository contract, the in-memory repository, and the Blackboard context loader. It depends only on core runtime types and can be used without Spring, JDBC, LLM, governance, or console modules.

The Memory Spring Boot starter depends on `actiongraph-memory` instead of the runtime starter. It provides Spring defaults for `MemoryRepository` and `MemoryContextLoader`; it also wires the JDBC memory repository when the shared JDBC persistence switch is enabled.

The Memory JDBC modules provide durable `MemoryRepository` storage without forcing core JDBC users to depend on memory contracts.

The Interpretation module provides GoalCatalog metadata, GoalInterpreter contracts, interpretation results, missing-field clarification types, and Blackboard seeders. It depends only on core planning/runtime types and can be used without LLM providers.

The Runtime API module composes interpretation, Blackboard seeding, `GoapExecutor`, and `ActionRegistry` into stable `interpret`, `start`, and `resume` service methods for gateways, CLIs, or custom controllers. It depends on core and interpretation contracts, but it does not provide an LLM provider, create repositories, or expose HTTP endpoints.

The Component Catalog module publishes static ActionGraph module metadata, capability tags, dependency hints, and composition profiles. It is Java 8 compatible, has no runtime dependency, and can be used by CLIs, deployment checks, enterprise gateways, or custom consoles. The Component Catalog Spring Boot starter exposes that metadata through read-only HTTP endpoints and remains opt-in through its own property switch.

The Control-Plane API module standardizes small response contracts such as `ControlPlaneErrorResponse` for endpoint starters and custom gateways. It depends only on the JDK, can be used without Spring, and intentionally does not map exceptions or register HTTP advice.

The Human Review module provides pending review tasks, approval chains, in-memory review storage, repository-backed review policy, callback handling, stable task response DTOs, and decision operations for approval inboxes, CLIs, gateways, or web controllers. It depends only on core policy/action/planning types and can be used without Spring MVC, JDBC, governance, or console modules.

The Governance Spring Boot starter depends on core policy contracts and Spring auto-configuration. It activates optional masking, amount-limit, and permission policy beans from configuration, but it does not register actions, persist state, route approval chains, or expose endpoints.

The Human Review Governance module depends on governance and human-review contracts. It contributes amount review attributes and risk-based approval-chain routing only when those human-review semantics are needed.

The Human Review starter depends on `actiongraph-human-review` instead of the runtime starter. It provides Spring defaults for `HumanReviewRepository`, `ApprovalChainResolver`, and `RepositoryBackedHumanReviewPolicy`; it also wires the JDBC review repository when the shared JDBC persistence switch is enabled.

The Human Review JDBC modules provide durable review-task storage without forcing core JDBC users to depend on human-review contracts.

The Console library defines the read-only monitoring service, response models, paging validation, `ConsoleRunRepository` port, JDBC trace read-model adapter, and CSV/JSONL export service. It stays usable by batch jobs, CLIs, custom gateways, or non-Spring control planes without exposing HTTP endpoints. The Console Spring Boot starter creates the service from any repository, can auto-configure the read-model repository from a `DataSource`, serves the built-in page, and exposes JSON/export endpoints behind the same `actiongraph.console.*` gates.

The Control Plane Spring Boot starter is a convenience aggregate over the Spring MVC endpoint starters for runtime entry, human-review task/callback APIs, and the Console surface. It has no production Java code and does not create runtime beans; Console read-model repository wiring remains property-gated by the Console starter.

## Boundary

`actiongraph-bom` is a version alignment platform. It has no runtime code and must not introduce transitive application dependencies.

`actiongraph-annotations` is an optional public framework adapter: it maps ordinary Java methods into core `Action` implementations. It must depend only on core contracts and must not depend on Spring, persistence, providers, or endpoints.

`actiongraph-memory` is an optional public framework component: it maps durable business memory into typed Blackboard context. It must depend only on core contracts and must not depend on Spring, JDBC, LLM providers, governance, or endpoints.

`actiongraph-interpretation` is an optional public framework component: it maps natural-language or rule-based entry results into typed Goals and Blackboard seed data. It must depend only on core contracts and must not call LLM providers, register actions, persist state, or expose endpoints.

`actiongraph-runtime-api` is an optional public framework component: it maps interpretation and runtime wiring into stable entry service DTOs. It may start or resume runs through a supplied `GoapExecutor`, but it must not call LLM providers, create persistence, register actions, expose HTTP endpoints, or own approval-task/console concerns.

`actiongraph-component-catalog` is an optional ecosystem metadata component: it exposes static module and composition metadata through Java 8 compatible data classes and a service. It must not depend on runtime, Spring, JDBC, LLM providers, governance, repositories, or endpoints.

`actiongraph-control-plane-api` is an optional ecosystem utility: it exposes Java 8 compatible response DTO contracts, lightweight Runtime API and Component Catalog HTTP clients, and shared-secret token verification for control-plane adapters and legacy callers. It must not depend on Spring, runtime, persistence, LLM providers, governance, repositories, endpoint modules, or third-party HTTP/JSON libraries.

`actiongraph-human-review` is an optional public framework component: it maps high-risk runtime decisions into review tasks, external callbacks, and stable task query/decision service APIs. It must depend only on core contracts and must not expose HTTP endpoints or own durable persistence.

`actiongraph-spring-boot-starter` is part of the public framework integration surface: it registers actions and minimal runtime defaults. It must not bring memory, repository-backed review, JDBC persistence, or control-plane endpoints transitively.

`actiongraph-runtime-api-spring-boot-starter` is an ecosystem component: it exposes optional Spring MVC goal interpretation, run start, and resume endpoints over `ActionGraphRuntimeApiService`. It must not create interpreters, seeders, action registries, persistence repositories, review-task endpoints, callback endpoints, or console endpoints.

`actiongraph-component-catalog-spring-boot-starter` is an ecosystem component: it exposes optional read-only Spring MVC component catalog endpoints over `ActionGraphComponentCatalogService`. It must not create runtime beans, repositories, action registries, interpreters, review storage, callback endpoints, Console repositories, LLM clients, or governance policies.

`actiongraph-memory-spring-boot-starter` is an optional Spring adapter: it configures structured memory defaults and optional JDBC memory storage. It must not register actions, execute runs, or expose endpoints.

`actiongraph-governance` is an optional policy library: it provides reusable non-Spring masking, amount-limit, and rule-based permission implementations. It must depend only on core contracts and must not register actions, persist state, route approval chains, or expose endpoints.

`actiongraph-governance-human-review` is an optional human-review policy library: it provides review-attribute contribution and risk-based approval routing. It may depend on `actiongraph-human-review`, but base governance must not depend on it.

`actiongraph-governance-spring-boot-starter` is an optional policy adapter: it configures the base governance policy library through Spring Boot properties. It must not expose HTTP endpoints, write persistence state, or create human-review routing beans.

`actiongraph-governance-human-review-spring-boot-starter` is an optional human-review policy adapter: it configures review attributes and approval-chain routing through Spring Boot properties. It must not expose HTTP endpoints or own review-task storage.

`actiongraph-jdbc-spring-boot-starter` is an optional infrastructure adapter: it replaces core trace/suspend in-memory repository defaults with JDBC implementations when enabled. It must not register business actions, configure Memory/Human Review storage, or expose endpoints.

`actiongraph-persistence-jdbc` is a low-level core persistence library for trace, suspended runs, and trace read models. It is usable without Spring and is the dependency that specialized services can wire manually.

`actiongraph-memory-jdbc` and `actiongraph-human-review-jdbc` are optional low-level persistence libraries. They must depend on their domain contracts plus core JDBC helpers, and must not register Spring beans by themselves.

Memory JDBC Spring wiring lives in `actiongraph-memory-spring-boot-starter`; human-review JDBC Spring wiring lives in `actiongraph-human-review-spring-boot-starter`. Both are behind the shared JDBC persistence switch, and neither path exposes endpoints.

`actiongraph-human-review-spring-boot-starter` is an optional Spring adapter: it wires repository-backed review tasks, policy defaults, and optional JDBC review storage. It must not expose HTTP endpoints, execute, resume, or compensate runs.

`actiongraph-human-review-api-spring-boot-starter` is an ecosystem component: it exposes optional Spring MVC task query/decision endpoints over `HumanReviewApiService` and optional callback endpoints over `HumanReviewCallbackHandler`. It must not create review storage, execute, resume, or compensate runs.

`actiongraph-console` is an ecosystem component: it maps any `ConsoleRunRepository` implementation into stable read-only Console responses, adapts the JDBC trace read model when callers want to read ActionGraph trace tables directly, and formats run summaries or trace details as CSV/JSONL audit evidence. It must not depend on Spring Web, expose HTTP endpoints, or mutate runtime state.

`actiongraph-console-spring-boot-starter` is an ecosystem adapter: it creates `ActionGraphConsoleService`, can create a JDBC-backed `ConsoleRunRepository` from a `DataSource`, serves the built-in Console page, and exposes read-only JSON/export endpoints behind `actiongraph.console.*` property gates. It must not execute, resume, approve, deny, or compensate runs.

`actiongraph-control-plane-spring-boot-starter` is a convenience aggregate: it brings runtime entry, component catalog, human-review task/callback API, and Console endpoint starters together. It must not add production code, create runtime repositories, register actions, create interpreters, execute runs on its own, or change any endpoint's existing opt-in property gates.
