# Module Governance

ActionGraph is still before a formal 1.0 release, so module count is treated as a product decision, not a coding convenience.

## Rule

No new `actiongraph-*` module may be added without a PRD or ADR entry that explains:

- why the capability cannot live in an existing module
- the public API boundary and ownership
- compatibility target, especially whether Java 8 clients can load it
- BOM and component catalog impact
- migration or consolidation path if it is transitional

Until the module surface is consolidated to about ten modules, the default decision for new capabilities is to merge into an existing module.

## Consolidation Target

The target module surface is:

| Target module | Role |
|---|---|
| `actiongraph-bom` | Version platform |
| `actiongraph-core` | Runtime kernel and small pure Java contracts |
| `actiongraph-annotations` | Annotation action adapter |
| `actiongraph-human-review` | Human-review contracts, policies, callbacks, and storage adapters |
| `actiongraph-governance` | Masking, permission, amount, and approval-routing policies |
| `actiongraph-persistence-jdbc` | JDBC persistence for runtime, memory, review, and read models |
| `actiongraph-llm-deepseek` | LLM interpretation and DeepSeek-compatible provider wiring |
| `actiongraph-console` | Read-only console services and control-plane DTO/client helpers |
| `actiongraph-spring-boot-starter` | Main Spring Boot integration starter |
| `actiongraph-samples` | Samples only, not a reusable library |

`actiongraph-console-spring-boot-starter` may remain temporarily as the one optional UI/control-plane starter while the main starter stays business-runtime focused.

## Current Approval Ledger

This ledger is intentionally exhaustive. The component catalog test compares it with `settings.gradle.kts`; adding a module requires updating this table and therefore creates a reviewable diff.

<!-- module-governance:start -->
| Current module | Disposition | Consolidation target |
|---|---|---|
| `actiongraph-annotations` | keep-target | `actiongraph-annotations` |
| `actiongraph-bom` | keep-target | `actiongraph-bom` |
| `actiongraph-component-catalog` | merge-planned | `actiongraph-console` |
| `actiongraph-component-catalog-spring-boot-starter` | merge-planned | `actiongraph-spring-boot-starter` |
| `actiongraph-console` | keep-target | `actiongraph-console` |
| `actiongraph-console-spring-boot-starter` | temporary-optional-starter | `actiongraph-console-spring-boot-starter` |
| `actiongraph-control-plane-api` | merge-planned | `actiongraph-console` |
| `actiongraph-core` | keep-target | `actiongraph-core` |
| `actiongraph-governance` | keep-target | `actiongraph-governance` |
| `actiongraph-governance-human-review-spring-boot-starter` | merge-planned | `actiongraph-spring-boot-starter` |
| `actiongraph-governance-spring-boot-starter` | merge-planned | `actiongraph-spring-boot-starter` |
| `actiongraph-human-review` | keep-target | `actiongraph-human-review` |
| `actiongraph-human-review-api-spring-boot-starter` | merge-planned | `actiongraph-spring-boot-starter` |
| `actiongraph-human-review-spring-boot-starter` | merge-planned | `actiongraph-spring-boot-starter` |
| `actiongraph-interpretation` | merge-planned | `actiongraph-core` |
| `actiongraph-jdbc-spring-boot-starter` | merge-planned | `actiongraph-spring-boot-starter` |
| `actiongraph-llm` | merge-planned | `actiongraph-llm-deepseek` |
| `actiongraph-llm-deepseek` | keep-target | `actiongraph-llm-deepseek` |
| `actiongraph-memory` | merge-planned | `actiongraph-core` |
| `actiongraph-memory-spring-boot-starter` | merge-planned | `actiongraph-spring-boot-starter` |
| `actiongraph-persistence-jdbc` | keep-target | `actiongraph-persistence-jdbc` |
| `actiongraph-runtime-api` | merge-planned | `actiongraph-core` |
| `actiongraph-runtime-api-spring-boot-starter` | merge-planned | `actiongraph-spring-boot-starter` |
| `actiongraph-samples` | sample-only | `actiongraph-samples` |
| `actiongraph-spring-boot-starter` | keep-target | `actiongraph-spring-boot-starter` |
<!-- module-governance:end -->

## Freeze Notes

- The ledger does not mean the current 25-module surface is desirable; it documents the temporary state so growth is visible.
- Consolidation should happen by moving implementation into target modules, keeping compatibility shims only when a consumer migration requires them.
- Sample-domain growth is not a substitute for F1 validation. Claims precheck is frozen except for real/near-real integration fixes and evidence.
