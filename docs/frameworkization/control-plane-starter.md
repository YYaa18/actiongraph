# Control-Plane Endpoint Composition

ActionGraph no longer publishes an extra aggregate starter or one-artifact-per-endpoint starters for the built-in Spring MVC control-plane endpoints. Spring deployments compose the endpoint surface with the main starter and explicit property switches.

Use these dependencies when one deployment should expose the full built-in control plane:

- `actiongraph-spring-boot-starter`

Runtime entry, component catalog, human-review task, human-review callback, and Console endpoints live in the main starter but remain independently disabled by default.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.2.0-SNAPSHOT"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
}
```

The starter set does not include business actions, domain-specific interpreters, or LLM clients. Register those explicitly in the application.

Built-in endpoints share `actiongraph-control-plane-api` for error response contracts, Java 8 compatible aggregate / properties-based aggregate / safe GET retry / Runtime / Component Catalog / Human Review / Console HTTP client support, and shared-secret token checks. The API component keeps the JSON error shape, header lookup, disabled-secret semantics, and constant-time comparison consistent across Runtime API, Component Catalog, Human Review API, callback, and Console endpoints. The Java 8 aggregate client, properties adapter, and GET-only retry knobs are caller-side conveniences for legacy systems. These are still lightweight control-plane utilities; enterprise identity, gateway policy, RBAC, tenant checks, and rate limits remain outside ActionGraph.

Runtime start/resume endpoints also support whitelisted request-header capture into trace metadata through `actiongraph.runtime.api.trace-headers`. This is intended for non-sensitive audit identifiers such as request id, correlation id, or source system. The configured Runtime API token header is hard-excluded from trace capture even when misconfigured into that list; other sensitive headers should still stay out of it.

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

The starters add classpath availability only. Existing conditional beans still require the corresponding runtime services, repositories, interpreters, seeders, or console repositories. The component catalog is self-contained, but it still remains opt-in through `actiongraph.component-catalog.enabled=true`.

## Component Catalog Endpoints

When the component catalog switch is enabled, the main Spring starter exposes a read-only ecosystem view:

```text
GET /actiongraph/components
GET /actiongraph/components/modules
GET /actiongraph/components/modules/{module}
GET /actiongraph/components/modules/{module}/profiles
GET /actiongraph/components/compatibility/{compatibility}
GET /actiongraph/components/profiles
GET /actiongraph/components/profiles/{profile}
```

Use `actiongraph.component-catalog.path`, `actiongraph.component-catalog.token-header`, and `actiongraph.component-catalog.shared-secret` to customize path and access control. The catalog endpoints do not execute runs, approve tasks, create repositories, or inspect application secrets; they return static module and composition metadata from `actiongraph-control-plane-api`.

## Boundary

There is no separate aggregate module for this composition. Keep endpoint exposure explicit in application dependencies and property switches.
