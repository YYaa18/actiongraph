# Ecosystem Modularity

ActionGraph is split so the public runtime framework and optional ecosystem/control-plane components can be adopted independently.

## Layers

| Layer | Modules | Responsibility |
|---|---|---|
| Version platform | `actiongraph-bom` | Aligns all ActionGraph module versions for mix-and-match adoption |
| Runtime kernel | `actiongraph-core` | Action SPI, planner, executor, policy, trace, memory, interpretation contracts |
| Optional adapters | `actiongraph-spring-boot-starter`, `actiongraph-governance`, `actiongraph-governance-spring-boot-starter`, `actiongraph-jdbc-spring-boot-starter`, `actiongraph-llm-deepseek`, `actiongraph-persistence-jdbc` | Spring runtime wiring, reusable governance policies, Spring governance wiring, Spring JDBC repository wiring, LLM goal interpretation, low-level durable repositories |
| Control-plane ecosystem | `actiongraph-human-review-spring-boot-starter`, `actiongraph-console-core`, `actiongraph-console-jdbc`, `actiongraph-console-spring-boot-starter` | Approval callback endpoints, read-only Console query service, JDBC Console adapter, operational Console UI and query endpoints |
| Samples | `actiongraph-samples` | Reference domains and batch demos; not published as a library |

## Composition Rules

- Consumers should import `actiongraph-bom` first, then choose the modules they need without repeating versions.
- A pure Java service can depend only on `actiongraph-core`.
- A Spring Boot business service can depend on `actiongraph-spring-boot-starter` without exposing any HTTP control-plane endpoint.
- Non-Spring masking, amount-limit, approval routing, and rule-based permission policies add `actiongraph-governance`.
- Spring Boot masking, amount-limit, and risk-based approval governance add `actiongraph-governance-spring-boot-starter`.
- Durable Spring Boot production runs add `actiongraph-jdbc-spring-boot-starter`; non-Spring/manual runtimes add `actiongraph-persistence-jdbc`.
- Natural-language goal interpretation adds `actiongraph-llm-deepseek`.
- External approval callbacks add `actiongraph-human-review-spring-boot-starter`.
- Custom operational monitoring adds `actiongraph-console-core`; JDBC-backed custom monitoring also adds `actiongraph-console-jdbc`; Spring MVC operational monitoring adds `actiongraph-console-spring-boot-starter`.

The JDBC Spring Boot starter depends on the low-level JDBC repositories and the Spring `DataSource` contract. It creates durable repository beans only when `actiongraph.persistence.jdbc.enabled=true`, and it does not register actions, execute runs, expose HTTP endpoints, or start any control-plane surface.

The Governance Spring Boot starter depends on core policy contracts and Spring auto-configuration. It activates optional policy beans from configuration, but it does not register actions, persist state, or expose endpoints.

The Human Review starter depends on the core review repository contract instead of the runtime starter. This makes it usable both inside a business service and inside a separate approval callback receiver, as long as the application provides a `HumanReviewRepository`.

The Console core defines the read-only monitoring service, response models, paging validation, and `ConsoleRunRepository` port. It depends only on core trace types, not JDBC or Spring Web. The Console JDBC adapter maps the JDBC trace read model into that port. The Spring Boot Console starter combines the core service, JDBC adapter, and a thin HTTP/UI layer.

## Boundary

`actiongraph-bom` is a version alignment platform. It has no runtime code and must not introduce transitive application dependencies.

`actiongraph-spring-boot-starter` is part of the public framework integration surface: it registers actions, runtime defaults, policies, and repositories.

`actiongraph-governance` is an optional policy library: it provides reusable non-Spring masking, amount-limit, approval routing, and rule-based permission implementations. It must depend only on core contracts and must not register actions, persist state, or expose endpoints.

`actiongraph-governance-spring-boot-starter` is an optional policy adapter: it configures the governance policy library through Spring Boot properties. It must not expose HTTP endpoints or write persistence state.

`actiongraph-jdbc-spring-boot-starter` is an optional infrastructure adapter: it replaces in-memory repository defaults with JDBC implementations when enabled. It must not register business actions or expose endpoints.

`actiongraph-persistence-jdbc` is a low-level persistence library. It is usable without Spring and is the dependency that specialized services can wire manually.

`actiongraph-human-review-spring-boot-starter` is an ecosystem component: it receives external approval decisions and writes them through `HumanReviewCallbackHandler`. It must not execute, resume, or compensate runs.

`actiongraph-console-core` is an ecosystem component: it maps any `ConsoleRunRepository` implementation into stable read-only Console responses and validates paging. It must not depend on Spring Web, JDBC, or mutate runtime state.

`actiongraph-console-jdbc` is an ecosystem adapter: it implements the Console repository port through the JDBC trace read model. It must remain read-only and must not configure HTTP endpoints.

`actiongraph-console-spring-boot-starter` is an ecosystem component: it renders a page and exposes read-only query endpoints by delegating to `actiongraph-console-core` and the default JDBC adapter. It must not execute, resume, approve, deny, or compensate runs.
