# Ecosystem Modularity

ActionGraph is split so the public runtime framework and optional ecosystem/control-plane components can be adopted independently.

## Layers

| Layer | Modules | Responsibility |
|---|---|---|
| Version platform | `actiongraph-bom` | Aligns all ActionGraph module versions for mix-and-match adoption |
| Runtime kernel | `actiongraph-core` | Action SPI, planner, executor, policy, trace |
| Optional adapters | `actiongraph-annotations`, `actiongraph-memory`, `actiongraph-memory-spring-boot-starter`, `actiongraph-memory-jdbc`, `actiongraph-memory-jdbc-spring-boot-starter`, `actiongraph-interpretation`, `actiongraph-human-review`, `actiongraph-human-review-api`, `actiongraph-human-review-jdbc`, `actiongraph-human-review-jdbc-spring-boot-starter`, `actiongraph-human-review-spring-boot-starter`, `actiongraph-spring-boot-starter`, `actiongraph-governance`, `actiongraph-governance-human-review`, `actiongraph-governance-spring-boot-starter`, `actiongraph-governance-human-review-spring-boot-starter`, `actiongraph-jdbc-spring-boot-starter`, `actiongraph-llm`, `actiongraph-llm-deepseek`, `actiongraph-persistence-jdbc` | Pure Java annotation action registration, structured memory context, Spring memory wiring, JDBC memory storage, goal interpretation contracts, repository-backed human review, reusable human-review task API service, JDBC review storage, Spring review-policy wiring, Spring runtime wiring, reusable governance policies, human-review governance extensions, Spring governance wiring, Spring core JDBC wiring, provider-neutral LLM goal interpretation, DeepSeek provider adapter, low-level durable repositories |
| Control-plane ecosystem | `actiongraph-human-review-api-spring-boot-starter`, `actiongraph-human-review-callback-spring-boot-starter`, `actiongraph-console-core`, `actiongraph-console-jdbc`, `actiongraph-console-export`, `actiongraph-console-spring-boot-autoconfigure`, `actiongraph-console-api-spring-boot-starter`, `actiongraph-console-ui-spring-boot-starter`, `actiongraph-console-export-spring-boot-starter`, `actiongraph-console-jdbc-spring-boot-starter`, `actiongraph-console-spring-boot-starter` | Human-review task query/decision HTTP endpoints, approval callback HTTP endpoint, read-only Console query service, JDBC Console adapter, CSV/JSONL audit export service, shared Spring Console service auto-configuration, optional JSON API, optional HTML UI, optional export endpoints, Spring JDBC Console repository auto-configuration, compatibility aggregate starter |
| Samples | `actiongraph-samples` | Reference domains and batch demos; not published as a library |

## Composition Rules

- Consumers should import `actiongraph-bom` first, then choose the modules they need without repeating versions.
- A pure Java service can depend only on `actiongraph-core`.
- Pure Java annotation-based registration adds `actiongraph-annotations`.
- Structured long-term memory adds `actiongraph-memory`.
- Spring Boot structured memory defaults add `actiongraph-memory-spring-boot-starter`.
- JDBC structured memory adds `actiongraph-memory-jdbc`; Spring Boot JDBC memory adds `actiongraph-memory-jdbc-spring-boot-starter`.
- Goal catalogs, rule-based interpreters, and Goal-to-Blackboard seeding add `actiongraph-interpretation`.
- Repository-backed external review tasks and callback handling add `actiongraph-human-review`; stable task query/decision projections add `actiongraph-human-review-api`.
- JDBC review task storage adds `actiongraph-human-review-jdbc`; Spring Boot JDBC review task storage adds `actiongraph-human-review-jdbc-spring-boot-starter`.
- A Spring Boot business service can depend on `actiongraph-spring-boot-starter` without exposing any HTTP control-plane endpoint.
- Non-Spring masking, amount-limit, and rule-based permission policies add `actiongraph-governance`; human-review approval routing or review attributes add `actiongraph-governance-human-review`.
- Spring Boot masking and amount-limit governance add `actiongraph-governance-spring-boot-starter`; risk-based approval routing and review attributes add `actiongraph-governance-human-review-spring-boot-starter`.
- Durable Spring Boot production runs add `actiongraph-jdbc-spring-boot-starter`; non-Spring/manual runtimes add `actiongraph-persistence-jdbc`. Memory and review-task durability are opt-in through their own JDBC modules.
- Provider-neutral natural-language goal interpretation adds `actiongraph-llm`.
- DeepSeek-compatible model access adds `actiongraph-llm-deepseek`.
- Spring Boot repository-backed approval tasks add `actiongraph-human-review-spring-boot-starter`; Spring MVC approval task APIs add `actiongraph-human-review-api-spring-boot-starter`; Spring MVC external approval callbacks add `actiongraph-human-review-callback-spring-boot-starter`.
- Custom operational monitoring adds `actiongraph-console-core`; JDBC-backed custom monitoring also adds `actiongraph-console-jdbc`; CSV/JSONL audit export adds `actiongraph-console-export`; Spring MVC API-only monitoring adds `actiongraph-console-api-spring-boot-starter`; Spring MVC page-only monitoring adds `actiongraph-console-ui-spring-boot-starter`; Spring MVC export-only monitoring adds `actiongraph-console-export-spring-boot-starter`; old-style full Spring MVC monitoring adds the compatibility `actiongraph-console-spring-boot-starter`; Spring MVC JDBC-backed monitoring also adds `actiongraph-console-jdbc-spring-boot-starter`.

The JDBC Spring Boot starter depends on the low-level core JDBC repositories and the Spring `DataSource` contract. It creates durable trace/suspended-run repository beans only when `actiongraph.persistence.jdbc.enabled=true`, and it does not register actions, execute runs, expose HTTP endpoints, configure Memory/Human Review storage, or start any control-plane surface.

The LLM module provides provider-neutral goal interpretation, GoalCatalog prompt rendering, and structured output parsing. Provider modules such as `actiongraph-llm-deepseek` depend on it and add only transport/model-specific clients.

The Memory module provides structured memory records, the repository contract, the in-memory repository, and the Blackboard context loader. It depends only on core runtime types and can be used without Spring, JDBC, LLM, governance, or console modules.

The Memory Spring Boot starter depends on `actiongraph-memory` instead of the runtime starter. It provides Spring defaults for `MemoryRepository` and `MemoryContextLoader` and backs off when JDBC or application beans provide them.

The Memory JDBC modules provide durable `MemoryRepository` storage without forcing core JDBC users to depend on memory contracts.

The Interpretation module provides GoalCatalog metadata, GoalInterpreter contracts, interpretation results, missing-field clarification types, and Blackboard seeders. It depends only on core planning/runtime types and can be used without LLM providers.

The Human Review module provides pending review tasks, approval chains, in-memory review storage, repository-backed review policy, and callback handling. It depends only on core policy/action/planning types and can be used without Spring MVC, JDBC, governance, or console modules.

The Human Review API module maps `HumanReviewRepository` into stable task response DTOs and decision operations for approval inboxes, CLIs, gateways, or web controllers. It depends on `actiongraph-human-review` and does not expose HTTP endpoints.

The Governance Spring Boot starter depends on core policy contracts and Spring auto-configuration. It activates optional masking, amount-limit, and permission policy beans from configuration, but it does not register actions, persist state, route approval chains, or expose endpoints.

The Human Review Governance module depends on governance and human-review contracts. It contributes amount review attributes and risk-based approval-chain routing only when those human-review semantics are needed.

The Human Review starter depends on `actiongraph-human-review` instead of the runtime starter. It provides Spring defaults for `HumanReviewRepository`, `ApprovalChainResolver`, and `RepositoryBackedHumanReviewPolicy`, and it can also expose the optional callback receiver.

The Human Review JDBC modules provide durable review-task storage without forcing core JDBC users to depend on human-review contracts.

The Console core defines the read-only monitoring service, response models, paging validation, and `ConsoleRunRepository` port. It depends only on core trace types, not JDBC or Spring Web. The Console JDBC adapter maps the JDBC trace read model into that port. The Console export module formats run summaries and trace details as CSV/JSONL over `ActionGraphConsoleService` and stays usable by batch jobs, CLIs, or custom gateways without Spring. The Console shared Spring Boot auto-configuration creates the `ActionGraphConsoleService` from any repository without exposing endpoints. The Console API starter exposes only JSON query endpoints. The Console UI starter exposes only the built-in page. The Console export Spring Boot starter exposes only CSV/JSONL audit export endpoints. The Console JDBC Spring Boot starter auto-configures the read-model repository from a `DataSource`. The legacy Console Spring Boot starter is a compatibility aggregate over API and UI.

## Boundary

`actiongraph-bom` is a version alignment platform. It has no runtime code and must not introduce transitive application dependencies.

`actiongraph-annotations` is an optional public framework adapter: it maps ordinary Java methods into core `Action` implementations. It must depend only on core contracts and must not depend on Spring, persistence, providers, or endpoints.

`actiongraph-memory` is an optional public framework component: it maps durable business memory into typed Blackboard context. It must depend only on core contracts and must not depend on Spring, JDBC, LLM providers, governance, or endpoints.

`actiongraph-interpretation` is an optional public framework component: it maps natural-language or rule-based entry results into typed Goals and Blackboard seed data. It must depend only on core contracts and must not call LLM providers, register actions, persist state, or expose endpoints.

`actiongraph-human-review` is an optional public framework component: it maps high-risk runtime decisions into review tasks and external callbacks. It must depend only on core contracts and must not expose HTTP endpoints or own durable persistence.

`actiongraph-human-review-api` is an optional public framework component: it maps review repositories into stable task query and decision service APIs. It must not expose HTTP endpoints, create review storage, execute, resume, or compensate runs.

`actiongraph-spring-boot-starter` is part of the public framework integration surface: it registers actions and minimal runtime defaults. It must not bring memory, repository-backed review, JDBC persistence, or control-plane endpoints transitively.

`actiongraph-memory-spring-boot-starter` is an optional Spring adapter: it configures structured memory defaults. It must not register actions, execute runs, or expose endpoints.

`actiongraph-governance` is an optional policy library: it provides reusable non-Spring masking, amount-limit, and rule-based permission implementations. It must depend only on core contracts and must not register actions, persist state, route approval chains, or expose endpoints.

`actiongraph-governance-human-review` is an optional human-review policy library: it provides review-attribute contribution and risk-based approval routing. It may depend on `actiongraph-human-review`, but base governance must not depend on it.

`actiongraph-governance-spring-boot-starter` is an optional policy adapter: it configures the base governance policy library through Spring Boot properties. It must not expose HTTP endpoints, write persistence state, or create human-review routing beans.

`actiongraph-governance-human-review-spring-boot-starter` is an optional human-review policy adapter: it configures review attributes and approval-chain routing through Spring Boot properties. It must not expose HTTP endpoints or own review-task storage.

`actiongraph-jdbc-spring-boot-starter` is an optional infrastructure adapter: it replaces core trace/suspend in-memory repository defaults with JDBC implementations when enabled. It must not register business actions, configure Memory/Human Review storage, or expose endpoints.

`actiongraph-persistence-jdbc` is a low-level core persistence library for trace, suspended runs, and trace read models. It is usable without Spring and is the dependency that specialized services can wire manually.

`actiongraph-memory-jdbc` and `actiongraph-human-review-jdbc` are optional low-level persistence libraries. They must depend on their domain contracts plus core JDBC helpers, and must not register Spring beans by themselves.

`actiongraph-memory-jdbc-spring-boot-starter` and `actiongraph-human-review-jdbc-spring-boot-starter` are optional infrastructure adapters. They create only their corresponding JDBC repository bean and must not expose endpoints.

`actiongraph-human-review-spring-boot-starter` is an optional Spring adapter: it wires repository-backed review tasks and policy defaults. It must not expose HTTP endpoints, execute, resume, or compensate runs.

`actiongraph-human-review-api-spring-boot-starter` is an ecosystem component: it exposes optional Spring MVC task query and decision endpoints over `HumanReviewApiService`. It must not create review storage, expose callback endpoints, execute, resume, or compensate runs.

`actiongraph-human-review-callback-spring-boot-starter` is an ecosystem component: it exposes an optional Spring MVC callback endpoint that writes external approval decisions through `HumanReviewCallbackHandler` from `actiongraph-human-review`. It must not create review storage, execute, resume, or compensate runs.

`actiongraph-console-core` is an ecosystem component: it maps any `ConsoleRunRepository` implementation into stable read-only Console responses and validates paging. It must not depend on Spring Web, JDBC, or mutate runtime state.

`actiongraph-console-jdbc` is an ecosystem adapter: it implements the Console repository port through the JDBC trace read model. It must remain read-only and must not configure HTTP endpoints.

`actiongraph-console-export` is an ecosystem component: it formats read-only run summaries and trace details as CSV or JSONL through `ActionGraphConsoleService`. It must not depend on Spring Web, JDBC, or mutate runtime state.

`actiongraph-console-spring-boot-autoconfigure` is a shared Spring adapter: it creates `ActionGraphConsoleService` and binds `actiongraph.console.*` properties. It must not expose HTTP endpoints, serve pages, create JDBC repositories, or mutate runtime state.

`actiongraph-console-api-spring-boot-starter` is an ecosystem component: it exposes only read-only JSON query endpoints over `ActionGraphConsoleService`. It must not render the bundled page, execute, resume, approve, deny, or compensate runs.

`actiongraph-console-ui-spring-boot-starter` is an ecosystem component: it exposes only the built-in HTML Console page. It must not expose JSON query endpoints, create repositories, execute, resume, approve, deny, or compensate runs.

`actiongraph-console-export-spring-boot-starter` is an ecosystem component: it exposes only CSV/JSONL audit export endpoints over `ActionGraphConsoleExportService`. It must not expose the bundled page, regular JSON query endpoints, create repositories, execute, resume, approve, deny, or compensate runs.

`actiongraph-console-jdbc-spring-boot-starter` is an ecosystem adapter: it creates a JDBC-backed `ConsoleRunRepository` when enabled. It must not expose HTTP endpoints.

`actiongraph-console-spring-boot-starter` is a compatibility aggregate: it brings the Console API and UI starters together for existing users. New services should prefer the split API/UI starters when they need finer endpoint control.
