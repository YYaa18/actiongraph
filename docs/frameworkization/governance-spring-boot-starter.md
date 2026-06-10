# Governance Spring Boot Starter

`actiongraph-governance-spring-boot-starter` packages optional governance policies for Spring Boot services. It is separate from the base runtime starter so teams can adopt ActionGraph execution without automatically enabling financial governance behavior.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-governance-spring-boot-starter")
}
```

## What It Configures

The starter auto-configures these beans before the base runtime starter:

- `DataMaskingPolicy`
- `AmountExtractor`
- `PermissionPolicy`
- `ReviewAttributeContributor`
- `ApprovalChainResolver`

Every bean uses `@ConditionalOnMissingBean`, so application-provided policies always win.

## Properties

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
  human-review:
    risk-based-approval-chain: true
```

Without this module, the base `actiongraph-spring-boot-starter` intentionally ignores those governance property trees and keeps neutral defaults: no masking, default permission allow, no amount escalation, and single-stage review.

## Boundary

This starter does not register actions, execute runs, persist state, or expose HTTP endpoints. It only contributes policy beans that the base executor already consumes.
