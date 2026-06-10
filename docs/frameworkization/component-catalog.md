# Component Catalog

`actiongraph-component-catalog` is a reusable, non-Spring metadata component for ActionGraph's ecosystem modules.

It turns the module split into a machine-readable API: deployment checks, CLIs, enterprise gateways, custom consoles, or docs generators can query which modules exist, what capability tags they provide, what they require, which compatibility level they target, and which composition profiles are recommended.

## Pure Java Usage

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-component-catalog")
}
```

```java
ActionGraphComponentCatalogService catalog =
        ActionGraphComponentCatalogService.defaultCatalog();

catalog.component("actiongraph-runtime-api")
        .ifPresent(component -> System.out.println(component.capabilities()));

catalog.profile("full-control-plane")
        .ifPresent(profile -> System.out.println(profile.modules()));

catalog.componentsByCompatibility("java8-client")
        .forEach(component -> System.out.println(component.module()));
```

The pure Java module has no dependency on Spring, JDBC, LLM providers, runtime repositories, or business samples.

## Spring MVC Endpoint

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-component-catalog-spring-boot-starter")
}
```

```yaml
actiongraph:
  component-catalog:
    enabled: true
    path: /actiongraph/components
    token-header: X-ActionGraph-Catalog-Token
    shared-secret: ${ACTIONGRAPH_CATALOG_SECRET}
```

Endpoints:

```text
GET /actiongraph/components
GET /actiongraph/components/modules
GET /actiongraph/components/compatibility/{compatibility}
GET /actiongraph/components/modules/{module}
GET /actiongraph/components/profiles
GET /actiongraph/components/profiles/{profile}
```

The endpoint is read-only and property-gated. If `shared-secret` is configured, callers must send the configured token header.

## Composition Profiles

The default catalog includes profiles such as:

| Profile | Meaning |
|---|---|
| `core-runtime` | Smallest pure Java runtime kernel |
| `annotation-runtime` | Pure Java runtime with annotated business methods |
| `spring-business-runtime` | Spring Boot business runtime without public control-plane endpoints |
| `durable-spring-runtime` | Spring runtime with durable JDBC stores |
| `runtime-entry-api` | Interpret/start/resume service surface |
| `human-review-control-plane` | Approval task inbox and callback surface |
| `console-control-plane` | Read-only console and audit export surface |
| `full-control-plane` | Built-in endpoint aggregate |
| `ecosystem-introspection` | Component catalog modules |
| `java8-legacy-client` | Java 8 client-side integration over a deployed runtime API |
| `full-pilot-service` | Pilot-oriented full composition |

Profiles are guidance, not magic auto-installers. A service still chooses dependencies explicitly through Gradle/Maven and enables each endpoint family through its own `actiongraph.*` property.

## Compatibility Labels

Every component includes a `compatibility` value. Current labels are:

| Label | Meaning |
|---|---|
| `no-runtime-code` | No loadable runtime classes, such as the BOM |
| `java8-client` | Java 8 loadable client/control-plane helper; currently `actiongraph-control-plane-api` |
| `java8-runtime` | Reserved for a future embeddable Java 8 runtime slice |
| `java21-plus` | Current runtime, framework, infrastructure, governance, provider, and Spring modules |
| `sample-only` | Demonstration code only |

This lets old Java estates ask the catalog which artifacts are safe to load directly instead of guessing from artifact names.

## Release Gate

The catalog is part of the release contract, not only documentation. Tests verify:

- every module in `settings.gradle.kts` appears in the default catalog
- every non-sample library module appears in the BOM constraints
- every catalog component uses one of the closed compatibility labels
- every module listed as Java 8 compatible by the build passes the Java 8 bytecode and dependency guard
- the Java 8 control-plane client API can be consumed from standalone `javac --release 8` source

Adding a module therefore requires updating the catalog, assigning compatibility, and deciding whether the artifact belongs in the BOM.

## Boundary

The catalog does not inspect application runtime state, database contents, secrets, or the actual classpath. It returns static ActionGraph ecosystem metadata. Use it to standardize dependency guidance and control-plane discovery, not as a security scanner.

The Spring starter must not create runtime beans, repositories, LLM clients, review storage, action registries, Console repositories, approval callbacks, or business actions.
