# Control-Plane Shared Auth

`actiongraph-control-plane-auth` is a small pure Java component for consistent shared-secret token checks across ActionGraph control-plane endpoints.

It exists so endpoint starters and custom gateways do not each reimplement header lookup, disabled-secret handling, and constant-time token comparison.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-auth")
}
```

The module has no Spring, JDBC, LLM, runtime, or servlet dependency.

## Usage

```java
SharedSecretTokenProtection protection =
        new SharedSecretTokenProtection("X-ActionGraph-Runtime-Token", sharedSecret);

new ControlPlaneTokenVerifier().verify(
        protection,
        request.getHeader(protection.tokenHeader()),
        "runtime API token is missing or invalid"
);
```

Configuration-backed code can implement `SharedSecretTokenProperties`:

```java
public final class RuntimeApiProperties implements SharedSecretTokenProperties {
    private String tokenHeader = "X-ActionGraph-Runtime-Token";
    private String sharedSecret = "";

    @Override
    public String getTokenHeader() {
        return tokenHeader;
    }

    @Override
    public String getSharedSecret() {
        return sharedSecret;
    }
}
```

Then endpoint code can defer header lookup until a shared secret is actually enabled:

```java
tokenVerifier.verify(properties, headers::getFirst, "runtime API token is missing or invalid");
```

If `sharedSecret` is blank, verification succeeds and `headers::getFirst` is not called. If `tokenHeader` is blank, construction fails fast.

## Built-In Reuse

These Spring MVC endpoint starters depend on this module transitively:

- `actiongraph-runtime-api-spring-boot-starter`
- `actiongraph-component-catalog-spring-boot-starter`
- `actiongraph-human-review-api-spring-boot-starter`
- `actiongraph-human-review-callback-spring-boot-starter`
- `actiongraph-console-spring-boot-starter`

The aggregate `actiongraph-control-plane-spring-boot-starter` brings those endpoint starters together, so it also receives the shared auth behavior transitively.

For the matching error response DTO used by those endpoints, see [Control-Plane API Contracts](control-plane-api.md).

## Boundary

This component only standardizes ActionGraph's simple shared-secret endpoint guard. It is not enterprise IAM, OAuth, SSO, RBAC, tenant authorization, auditing, rate limiting, or gateway policy.

Production services should still put built-in endpoints behind the company's gateway and pair this token check with the deployment's normal identity and authorization controls.
