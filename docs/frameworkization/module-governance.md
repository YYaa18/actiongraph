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
| `actiongraph-core` | Runtime kernel, annotation action adapter, and small pure Java contracts |
| `actiongraph-control-plane-api` | Java 8 compatible component metadata, HTTP clients, and control-plane contracts |
| `actiongraph-human-review` | Human-review contracts, policies, callbacks, and storage adapters |
| `actiongraph-governance` | Masking, permission, amount, and approval-routing policies |
| `actiongraph-persistence-jdbc` | JDBC persistence for runtime, memory, review, and read models |
| `actiongraph-llm-deepseek` | LLM interpretation and DeepSeek-compatible provider wiring |
| `actiongraph-console` | Read-only console services and control-plane DTO/client helpers |
| `actiongraph-spring-boot-starter` | Main Spring Boot integration starter, including property-gated Console endpoints |
| `actiongraph-samples` | Samples only, not a reusable library |

## Current Approval Ledger

This ledger is intentionally exhaustive. The component catalog test compares it with `settings.gradle.kts`; adding a module requires updating this table and therefore creates a reviewable diff.

<!-- module-governance:start -->
| Current module | Disposition | Consolidation target |
|---|---|---|
| `actiongraph-bom` | keep-target | `actiongraph-bom` |
| `actiongraph-console` | keep-target | `actiongraph-console` |
| `actiongraph-control-plane-api` | keep-java8-client | `actiongraph-control-plane-api` |
| `actiongraph-core` | keep-target | `actiongraph-core` |
| `actiongraph-governance` | keep-target | `actiongraph-governance` |
| `actiongraph-human-review` | keep-target | `actiongraph-human-review` |
| `actiongraph-llm-deepseek` | keep-target | `actiongraph-llm-deepseek` |
| `actiongraph-persistence-jdbc` | keep-target | `actiongraph-persistence-jdbc` |
| `actiongraph-samples` | sample-only | `actiongraph-samples` |
| `actiongraph-spring-boot-starter` | keep-target | `actiongraph-spring-boot-starter` |
<!-- module-governance:end -->

## Freeze Notes

- The ledger documents the current 10-module surface so growth is visible and reviewable.
- Consolidation should happen by moving implementation into target modules, keeping compatibility shims only when a consumer migration requires them.
- Sample-domain growth is not a substitute for F1 validation. Claims precheck is frozen except for real/near-real integration fixes and evidence.
