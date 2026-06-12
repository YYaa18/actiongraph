# ActionGraph Learning Path

Start with the Golden Path, then move outward only when the integration needs
the next capability.

| Stage | Read | Outcome |
|---|---|---|
| L0 Hello Agent | `quick-start.html#l0`, `frameworkization/golden-path.md` | One annotated class and one `ActionGraph.start(...)` call |
| L1 Review and resume | `quick-start.html#l1`, `frameworkization/human-review.md`, `frameworkization/permission-policy.md` | Suspended run, external decision, same-run resume |
| L2 Natural language | `quick-start.html#l2`, `frameworkization/goal-catalog-prompt.md`, `frameworkization/llm-smoke.md` | `ActionGraph.chat(...)`, clarification, LLM or rule fallback |
| L3 Production | `quick-start.html#l3`, `frameworkization/jdbc-persistence.md`, `frameworkization/observability-spi.md`, `frameworkization/public-api-compatibility.md` | Durable trace/resume, masking, metrics, compatibility discipline |
| L4 Cross-service | `quick-start.html#l4`, `frameworkization/runtime-api.md`, `frameworkization/runtime-invocation-spi.md`, `frameworkization/control-plane-api.md` | HTTP gateways, Java 8 clients, callback/event boundaries |

## Packaging

Use these when a domain needs to be distributed as a reusable artifact:

- `frameworkization/extension-points.md`
- `frameworkization/dependency-composition.md`
- `frameworkization/module-governance.md`
- `frameworkization/component-catalog.md`

## SPI

Use these when the annotation model is not enough or when building framework
extensions:

- `frameworkization/annotation-action-usage.md`
- `frameworkization/blackboard-multi-instance.md`
- `frameworkization/api-stability-annotations.md`
- `frameworkization/spring-boot-starter.md`
- `frameworkization/control-plane-starter.md`
- `frameworkization/governance-spring-boot-starter.md`
- `frameworkization/java8-legacy-integration.md`
- `frameworkization/v2-suspend-resume.md`
- `frameworkization/v3-dynamic-repair.md`
- `frameworkization/v4-memory-context.md`
- `frameworkization/ms1-durability.md`

## Operations

Use these for deployment, audit, and pilot readiness:

- `frameworkization/claims-precheck-console.md`
- `frameworkization/claims-precheck-postgresql.md`
- `frameworkization/claims-precheck-review-callbacks.md`
- `f1-readiness-status.md`
- `f1-pilot-validation-pack.md`
- `dhk-integration.md`

## Internal Design Record

PRDs and strategy documents explain why a decision exists. They are useful for
review, but they are not the first integration path:

- `PRD-v0.md`
- `PRD-F0-finance.md`
- `PRD-DX1.md`
- `PRD-DX2.md`
- `PRD-MS1.md`
- `PRD-MS2.md`

