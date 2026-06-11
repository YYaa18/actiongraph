# Governance Spring Boot Starters

ActionGraph separates base governance from human-review governance so services can adopt only the policies they need.

## Base Governance Starter

`actiongraph-governance-spring-boot-starter` wraps the non-Spring `actiongraph-governance` policy library. It is separate from the base runtime starter so teams can adopt ActionGraph execution without automatically enabling financial governance behavior.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-governance-spring-boot-starter")
}
```

It auto-configures these beans before the base runtime starter:

- `DataMaskingPolicy`
- `AmountExtractor`
- `PermissionPolicy`

Every bean uses `@ConditionalOnMissingBean`, so application-provided policies always win. The starter activates:

```yaml
actiongraph:
  masking:
    enabled: true
    blocked-keys:
      - idCard
      - cardNo
      - customerName
  limits:
    rules:
      - action-id: sales.approval.request
        currency: CNY
        hard-limit: 1000000
        review-limit: 100000
```

The base governance starter does not create `ReviewAttributeContributor` or `ApprovalChainResolver` beans. Without the human-review extension, amount limits can deny above the hard limit, but they do not enrich approval tasks.

## Human-Review Governance Starter

`actiongraph-governance-human-review-spring-boot-starter` is an optional extension for approval flows. Add it only when review tasks should receive amount attributes or risk-based approval routing.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-governance-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-governance-human-review-spring-boot-starter")
}
```

It auto-configures:

- `ReviewAttributeContributor`
- `ApprovalChainResolver`

It reuses the same `actiongraph.limits.*` rules from the base governance starter and activates:

```yaml
actiongraph:
  human-review:
    risk-based-approval-chain: true
```

With this extension, amounts above `review-limit` are copied into `HumanReviewRequest.attributes` and the risk-based resolver can route HIGH-risk actions through checker and authorizer stages.

## Boundary

Neither starter registers actions, executes runs, persists state, or exposes HTTP endpoints. They only contribute policy beans that the base executor and human-review components already consume.

Non-Spring services can depend on `actiongraph-governance` directly and wire the same policy implementations by hand.
