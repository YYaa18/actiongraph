# Ecosystem Modularity

ActionGraph is split so the public runtime framework and optional ecosystem/control-plane components can be adopted independently.

## Layers

| Layer | Modules | Responsibility |
|---|---|---|
| Version platform | `actiongraph-bom` | Aligns all ActionGraph module versions for mix-and-match adoption |
| Runtime kernel | `actiongraph-core` | Action SPI, planner, executor, policy, trace, memory, interpretation contracts |
| Optional adapters | `actiongraph-spring-boot-starter`, `actiongraph-llm-deepseek`, `actiongraph-persistence-jdbc` | Spring wiring, LLM goal interpretation, durable repositories |
| Control-plane ecosystem | `actiongraph-human-review-spring-boot-starter`, `actiongraph-console-spring-boot-starter` | Approval callback endpoints, read-only operational Console UI and query endpoints |
| Samples | `actiongraph-samples` | Reference domains and batch demos; not published as a library |

## Composition Rules

- Consumers should import `actiongraph-bom` first, then choose the modules they need without repeating versions.
- A pure Java service can depend only on `actiongraph-core`.
- A Spring Boot business service can depend on `actiongraph-spring-boot-starter` without exposing any HTTP control-plane endpoint.
- Durable production runs add `actiongraph-persistence-jdbc`.
- Natural-language goal interpretation adds `actiongraph-llm-deepseek`.
- External approval callbacks add `actiongraph-human-review-spring-boot-starter`.
- Operational monitoring adds `actiongraph-console-spring-boot-starter`.

The Human Review starter depends on the core review repository contract instead of the runtime starter. This makes it usable both inside a business service and inside a separate approval callback receiver, as long as the application provides a `HumanReviewRepository`.

The Console starter depends on the JDBC read model instead of the runtime starter. This makes the control layer independently usable by a separate monitoring application that only has read access to the trace database.

## Boundary

`actiongraph-bom` is a version alignment platform. It has no runtime code and must not introduce transitive application dependencies.

`actiongraph-spring-boot-starter` is part of the public framework integration surface: it registers actions, runtime defaults, policies, and repositories.

`actiongraph-human-review-spring-boot-starter` is an ecosystem component: it receives external approval decisions and writes them through `HumanReviewCallbackHandler`. It must not execute, resume, or compensate runs.

`actiongraph-console-spring-boot-starter` is an ecosystem component: it reads trace data, renders a page, and exposes read-only query endpoints. It must not execute, resume, approve, deny, or compensate runs.
