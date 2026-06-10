# Control-Plane Starter

`actiongraph-control-plane-spring-boot-starter` is an optional aggregate for Spring MVC deployments that want the built-in control-plane endpoint set through one coordinate.

It brings these split endpoint starters together:

- `actiongraph-runtime-api-spring-boot-starter`
- `actiongraph-human-review-api-spring-boot-starter`
- `actiongraph-human-review-callback-spring-boot-starter`
- `actiongraph-console-api-spring-boot-starter`
- `actiongraph-console-ui-spring-boot-starter`
- `actiongraph-console-export-spring-boot-starter`

The split starters remain independently usable. Prefer them when a deployment should expose only one surface, such as API-only Console, approval callbacks only, or runtime start/resume only.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-spring-boot-starter")
}
```

The aggregate does not include runtime action registration, JDBC repositories, review-task storage, LLM clients, governance policies, or Console JDBC adapters. Add those components separately when the deployment owns them.

## Endpoint Switches

Each endpoint surface still uses its own explicit property switch:

```yaml
actiongraph:
  runtime:
    api:
      enabled: true
  human-review:
    api:
      enabled: true
    callback-endpoint:
      enabled: true
  console:
    enabled: true
```

The aggregate adds classpath availability only. Existing conditional beans still require the corresponding runtime services, repositories, interpreters, seeders, or console repositories.

## Boundary

This module has no production Java code. It is a dependency-composition artifact for convenience and version alignment. It must not add controllers, repositories, policies, execution behavior, or default storage of its own.
