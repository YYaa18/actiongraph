# Ecosystem Modularity

ActionGraph is split so the public runtime framework and optional ecosystem/control-plane components can be adopted independently.

## Layers

| Layer | Modules | Responsibility |
|---|---|---|
| Version platform | `actiongraph-bom` | Aligns all ActionGraph module versions for mix-and-match adoption |
| Runtime kernel | `actiongraph-core` | Action SPI, planner, executor, policy, trace |
| Optional adapters | `actiongraph-annotations`, `actiongraph-memory`, `actiongraph-memory-spring-boot-starter`, `actiongraph-interpretation`, `actiongraph-human-review`, `actiongraph-spring-boot-starter`, `actiongraph-governance`, `actiongraph-governance-spring-boot-starter`, `actiongraph-jdbc-spring-boot-starter`, `actiongraph-llm`, `actiongraph-llm-deepseek`, `actiongraph-persistence-jdbc` | Pure Java annotation action registration, structured memory context, Spring memory wiring, goal interpretation contracts, repository-backed human review, Spring runtime wiring, reusable governance policies, Spring governance wiring, Spring JDBC repository wiring, provider-neutral LLM goal interpretation, DeepSeek provider adapter, low-level durable repositories |
| Control-plane ecosystem | `actiongraph-human-review-spring-boot-starter`, `actiongraph-console-core`, `actiongraph-console-jdbc`, `actiongraph-console-spring-boot-starter` | Repository-backed review wiring, approval callback endpoints, read-only Console query service, JDBC Console adapter, operational Console UI and query endpoints |
| Samples | `actiongraph-samples` | Reference domains and batch demos; not published as a library |

## Composition Rules

- Consumers should import `actiongraph-bom` first, then choose the modules they need without repeating versions.
- A pure Java service can depend only on `actiongraph-core`.
- Pure Java annotation-based registration adds `actiongraph-annotations`.
- Structured long-term memory adds `actiongraph-memory`.
- Spring Boot structured memory defaults add `actiongraph-memory-spring-boot-starter`.
- Goal catalogs, rule-based interpreters, and Goal-to-Blackboard seeding add `actiongraph-interpretation`.
- Repository-backed external review tasks and callback handling add `actiongraph-human-review`.
- A Spring Boot business service can depend on `actiongraph-spring-boot-starter` without exposing any HTTP control-plane endpoint.
- Non-Spring masking, amount-limit, approval routing, and rule-based permission policies add `actiongraph-governance`.
- Spring Boot masking, amount-limit, and risk-based approval governance add `actiongraph-governance-spring-boot-starter`.
- Durable Spring Boot production runs add `actiongraph-jdbc-spring-boot-starter`; non-Spring/manual runtimes add `actiongraph-persistence-jdbc`.
- Provider-neutral natural-language goal interpretation adds `actiongraph-llm`.
- DeepSeek-compatible model access adds `actiongraph-llm-deepseek`.
- Spring Boot repository-backed approval tasks and external approval callbacks add `actiongraph-human-review-spring-boot-starter`.
- Custom operational monitoring adds `actiongraph-console-core`; JDBC-backed custom monitoring also adds `actiongraph-console-jdbc`; Spring MVC operational monitoring adds `actiongraph-console-spring-boot-starter`.

The JDBC Spring Boot starter depends on the low-level JDBC repositories and the Spring `DataSource` contract. It creates durable repository beans only when `actiongraph.persistence.jdbc.enabled=true`, and it does not register actions, execute runs, expose HTTP endpoints, or start any control-plane surface.

The LLM module provides provider-neutral goal interpretation, GoalCatalog prompt rendering, and structured output parsing. Provider modules such as `actiongraph-llm-deepseek` depend on it and add only transport/model-specific clients.

The Memory module provides structured memory records, the repository contract, the in-memory repository, and the Blackboard context loader. It depends only on core runtime types and can be used without Spring, JDBC, LLM, governance, or console modules.

The Memory Spring Boot starter depends on `actiongraph-memory` instead of the runtime starter. It provides Spring defaults for `MemoryRepository` and `MemoryContextLoader` and backs off when JDBC or application beans provide them.

The Interpretation module provides GoalCatalog metadata, GoalInterpreter contracts, interpretation results, missing-field clarification types, and Blackboard seeders. It depends only on core planning/runtime types and can be used without LLM providers.

The Human Review module provides pending review tasks, approval chains, in-memory review storage, repository-backed review policy, and callback handling. It depends only on core policy/action/planning types and can be used without Spring MVC, JDBC, governance, or console modules.

The Governance Spring Boot starter depends on core policy contracts and Spring auto-configuration. It activates optional policy beans from configuration, but it does not register actions, persist state, or expose endpoints.

The Human Review starter depends on `actiongraph-human-review` instead of the runtime starter. It provides Spring defaults for `HumanReviewRepository`, `ApprovalChainResolver`, and `RepositoryBackedHumanReviewPolicy`, and it can also expose the optional callback receiver.

The Console core defines the read-only monitoring service, response models, paging validation, and `ConsoleRunRepository` port. It depends only on core trace types, not JDBC or Spring Web. The Console JDBC adapter maps the JDBC trace read model into that port. The Spring Boot Console starter combines the core service, JDBC adapter, and a thin HTTP/UI layer.

## Boundary

`actiongraph-bom` is a version alignment platform. It has no runtime code and must not introduce transitive application dependencies.

`actiongraph-annotations` is an optional public framework adapter: it maps ordinary Java methods into core `Action` implementations. It must depend only on core contracts and must not depend on Spring, persistence, providers, or endpoints.

`actiongraph-memory` is an optional public framework component: it maps durable business memory into typed Blackboard context. It must depend only on core contracts and must not depend on Spring, JDBC, LLM providers, governance, or endpoints.

`actiongraph-interpretation` is an optional public framework component: it maps natural-language or rule-based entry results into typed Goals and Blackboard seed data. It must depend only on core contracts and must not call LLM providers, register actions, persist state, or expose endpoints.

`actiongraph-human-review` is an optional public framework component: it maps high-risk runtime decisions into review tasks and external callbacks. It must depend only on core contracts and must not expose HTTP endpoints or own durable persistence.

`actiongraph-spring-boot-starter` is part of the public framework integration surface: it registers actions and minimal runtime defaults. It must not bring memory, repository-backed review, JDBC persistence, or control-plane endpoints transitively.

`actiongraph-memory-spring-boot-starter` is an optional Spring adapter: it configures structured memory defaults. It must not register actions, execute runs, or expose endpoints.

`actiongraph-governance` is an optional policy library: it provides reusable non-Spring masking, amount-limit, approval routing, and rule-based permission implementations. It must depend only on core contracts and must not register actions, persist state, or expose endpoints.

`actiongraph-governance-spring-boot-starter` is an optional policy adapter: it configures the governance policy library through Spring Boot properties. It must not expose HTTP endpoints or write persistence state.

`actiongraph-jdbc-spring-boot-starter` is an optional infrastructure adapter: it replaces in-memory repository defaults with JDBC implementations when enabled. It must not register business actions or expose endpoints.

`actiongraph-persistence-jdbc` is a low-level persistence library. It is usable without Spring and is the dependency that specialized services can wire manually.

`actiongraph-human-review-spring-boot-starter` is an ecosystem component: it wires repository-backed review tasks and can receive external approval decisions through `HumanReviewCallbackHandler` from `actiongraph-human-review`. It must not execute, resume, or compensate runs.

`actiongraph-console-core` is an ecosystem component: it maps any `ConsoleRunRepository` implementation into stable read-only Console responses and validates paging. It must not depend on Spring Web, JDBC, or mutate runtime state.

`actiongraph-console-jdbc` is an ecosystem adapter: it implements the Console repository port through the JDBC trace read model. It must remain read-only and must not configure HTTP endpoints.

`actiongraph-console-spring-boot-starter` is an ecosystem component: it renders a page and exposes read-only query endpoints by delegating to `actiongraph-console-core` and the default JDBC adapter. It must not execute, resume, approve, deny, or compensate runs.
