# Control-Plane Starter

`actiongraph-control-plane-spring-boot-starter` is an optional aggregate for Spring MVC deployments that want the built-in control-plane endpoint set through one coordinate.

It brings these endpoint starters together:

- `actiongraph-runtime-api-spring-boot-starter`
- `actiongraph-component-catalog-spring-boot-starter`
- `actiongraph-human-review-api-spring-boot-starter`
- `actiongraph-console-spring-boot-starter`

The endpoint starters remain independently usable. Prefer them when a deployment should expose only one surface, such as Console monitoring only, human-review task/callback endpoints only, or runtime start/resume only.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-spring-boot-starter")
}
```

The aggregate does not include runtime action registration, runtime JDBC repositories, review-task storage, LLM clients, or governance policies. Add those components separately when the deployment owns them.

All built-in endpoint starters share `actiongraph-control-plane-api` for error response contracts, Java 8 compatible Runtime, Component Catalog, Human Review, and Console HTTP client support, and shared-secret token checks. The API component keeps the JSON error shape, header lookup, disabled-secret semantics, and constant-time comparison consistent across Runtime API, Component Catalog, Human Review API, callback, and Console endpoints. These are still lightweight control-plane utilities; enterprise identity, gateway policy, RBAC, tenant checks, and rate limits remain outside this aggregate.

Runtime start/resume endpoints also support whitelisted request-header capture into trace metadata through `actiongraph.runtime.api.trace-headers`. This is intended for non-sensitive audit identifiers such as request id, correlation id, or source system. Sensitive headers, including shared-secret tokens, should stay out of that list.

## Endpoint Switches

Each endpoint surface still uses its own explicit property switch:

```yaml
actiongraph:
  runtime:
    api:
      enabled: true
      trace-headers:
        - X-Request-Id
        - X-Correlation-Id
        - X-Source-System
  component-catalog:
    enabled: true
  human-review:
    api:
      enabled: true
    callback-endpoint:
      enabled: true
  console:
    enabled: true
```

The aggregate adds classpath availability only. Existing conditional beans still require the corresponding runtime services, repositories, interpreters, seeders, or console repositories. The component catalog is self-contained, but it still remains opt-in through `actiongraph.component-catalog.enabled=true`.

## Component Catalog Endpoints

When the component catalog switch is enabled, the aggregate also exposes a read-only ecosystem view:

```text
GET /actiongraph/components
GET /actiongraph/components/modules
GET /actiongraph/components/modules/{module}
GET /actiongraph/components/profiles
GET /actiongraph/components/profiles/{profile}
```

Use `actiongraph.component-catalog.path`, `actiongraph.component-catalog.token-header`, and `actiongraph.component-catalog.shared-secret` to customize path and access control. The catalog endpoints do not execute runs, approve tasks, create repositories, or inspect application secrets; they return static module and composition metadata from `actiongraph-component-catalog`.

## Boundary

This module has no production Java code. It is a dependency-composition artifact for convenience and version alignment. It must not add controllers, repositories, policies, execution behavior, or default storage of its own.
