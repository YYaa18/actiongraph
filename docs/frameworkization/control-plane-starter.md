# Control-Plane Endpoint Composition

ActionGraph no longer publishes an extra aggregate starter for the built-in Spring MVC control-plane endpoints. Spring deployments compose the endpoint surface explicitly so dependency lists show exactly what is exposed.

Use these endpoint starters together when one deployment should expose the full built-in control plane:

- `actiongraph-runtime-api-spring-boot-starter`
- `actiongraph-component-catalog-spring-boot-starter`
- `actiongraph-human-review-api-spring-boot-starter`
- `actiongraph-console-spring-boot-starter`

The endpoint starters remain independently usable. Prefer them when a deployment should expose only one surface, such as Console monitoring only, human-review task/callback endpoints only, or runtime start/resume only.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-runtime-api-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-component-catalog-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-human-review-api-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
}
```

The endpoint starter set does not include runtime action registration, runtime JDBC repositories, review-task storage, LLM clients, or governance policies. Add those components separately when the deployment owns them.

All built-in endpoint starters share `actiongraph-control-plane-api` for error response contracts, Java 8 compatible aggregate / properties-based aggregate / safe GET retry / Runtime / Component Catalog / Human Review / Console HTTP client support, and shared-secret token checks. The API component keeps the JSON error shape, header lookup, disabled-secret semantics, and constant-time comparison consistent across Runtime API, Component Catalog, Human Review API, callback, and Console endpoints. The Java 8 aggregate client, properties adapter, and GET-only retry knobs are caller-side conveniences for legacy systems. These are still lightweight control-plane utilities; enterprise identity, gateway policy, RBAC, tenant checks, and rate limits remain outside ActionGraph.

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

Endpoint starters add classpath availability only. Existing conditional beans still require the corresponding runtime services, repositories, interpreters, seeders, or console repositories. The component catalog is self-contained, but it still remains opt-in through `actiongraph.component-catalog.enabled=true`.

## Component Catalog Endpoints

When the component catalog switch is enabled, the component catalog endpoint starter exposes a read-only ecosystem view:

```text
GET /actiongraph/components
GET /actiongraph/components/modules
GET /actiongraph/components/modules/{module}
GET /actiongraph/components/modules/{module}/profiles
GET /actiongraph/components/compatibility/{compatibility}
GET /actiongraph/components/profiles
GET /actiongraph/components/profiles/{profile}
```

Use `actiongraph.component-catalog.path`, `actiongraph.component-catalog.token-header`, and `actiongraph.component-catalog.shared-secret` to customize path and access control. The catalog endpoints do not execute runs, approve tasks, create repositories, or inspect application secrets; they return static module and composition metadata from `actiongraph-component-catalog`.

## Boundary

There is no separate aggregate module for this composition. Keep endpoint exposure explicit in application dependencies and property switches.
