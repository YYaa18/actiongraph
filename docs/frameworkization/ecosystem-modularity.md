# Ecosystem Modularity

ActionGraph is split so the public runtime framework and optional ecosystem/control-plane components can be adopted independently.

## Layers

| Layer | Modules | Responsibility |
|---|---|---|
| Runtime kernel | `actiongraph-core` | Action SPI, planner, executor, policy, trace, memory, interpretation contracts |
| Optional adapters | `actiongraph-spring-boot-starter`, `actiongraph-llm-deepseek`, `actiongraph-persistence-jdbc` | Spring wiring, LLM goal interpretation, durable repositories |
| Control-plane ecosystem | `actiongraph-console-spring-boot-starter` | Read-only operational Console UI and Spring MVC query endpoints |
| Samples | `actiongraph-samples` | Reference domains and batch demos; not published as a library |

## Composition Rules

- A pure Java service can depend only on `actiongraph-core`.
- A Spring Boot business service can depend on `actiongraph-spring-boot-starter` without exposing any Console endpoint.
- Durable production runs add `actiongraph-persistence-jdbc`.
- Natural-language goal interpretation adds `actiongraph-llm-deepseek`.
- Operational monitoring adds `actiongraph-console-spring-boot-starter`.

The Console starter depends on the JDBC read model instead of the runtime starter. This makes the control layer independently usable by a separate monitoring application that only has read access to the trace database.

## Boundary

`actiongraph-spring-boot-starter` is part of the public framework integration surface: it registers actions, runtime defaults, policies, repositories, and human-review callbacks.

`actiongraph-console-spring-boot-starter` is an ecosystem component: it reads trace data, renders a page, and exposes read-only query endpoints. It must not execute, resume, approve, deny, or compensate runs.
