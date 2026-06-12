# Spring Governance Wiring

Spring Boot governance wiring now lives in `actiongraph-spring-boot-starter`. The reusable policy implementations remain in `actiongraph-governance`, so non-Spring services can still wire them manually.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.2.0-SNAPSHOT"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
}
```

## Configuration

```yaml
actiongraph:
  masking:
    enabled: true
    blocked-keys:
      - idCardNumber
      - bankAccount
  limits:
    rules:
      - action-id: sales.approval.request
        currency: CNY
        hard-limit: 1000000
        review-limit: 100000
  human-review:
    risk-based-approval-chain: true
```

The starter creates governance beans only when configuration asks for them, and every bean backs off when the application provides its own:

- `DataMaskingPolicy`
- `PermissionPolicy`
- `ReviewAttributeContributor`
- `ApprovalChainResolver`

`actiongraph.masking.enabled=true` activates regex masking for human-readable trace and review previews. `actiongraph.limits.rules` activates amount-limit permission checks. `actiongraph.human-review.risk-based-approval-chain=true` enables risk-based approval routing for repository-backed review tasks.

The framework does not infer business amounts. Applications that use amount limits should provide an `AmountExtractor` bean for their domain objects.
