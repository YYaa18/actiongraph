# F1 Readiness Status

This page is the current source of truth for the direction-correction work that happened before Java 8 legacy support. It separates completed local work from the external evidence still required to claim F1.

## Current Status

| Area | Status | Evidence |
|---|---|---|
| Module consolidation | Done | `settings.gradle.kts` has 10 `actiongraph-*` modules; only one `actiongraph-spring-boot-starter` remains |
| Module growth guard | Done | `docs/frameworkization/module-governance.md` requires PRD/ADR approval for new modules |
| Java 8 legacy boundary | Done | `actiongraph-control-plane-api` is the only `java8-client` module and is verified by `verifyJava8MavenConsumer` |
| Claims-precheck sample freeze | Done | `docs/f1-claims-precheck-notes.md` and `docs/finance-strategy.md` freeze new demo-only capabilities |
| Strategy wording guard | Done | Catalog tests reject wording that treats local samples as F1 completion |
| Pilot validation pack | Done | `docs/f1-pilot-validation-pack.md` defines entry criteria, field mapping, approval integration, metrics, Go/No-Go, and exit artifacts |
| Pilot evidence templates | Done | `docs/examples/f1-pilot-evidence-template/` contains sanitized templates for the F1 evidence folder |
| Pilot tracking issue form | Done | `.github/ISSUE_TEMPLATE/f1-pilot-validation.yml` captures the external evidence needed to close F1 |
| Real or near-real data source | Not done | Requires approved business data source, anonymized historical extract, reporting replica, or internal equivalent |
| Real or shadow approval path | Not done | Requires pending/approve/deny/duplicate/wrong-stage/bad-token evidence from a real or shadow approval source |
| Business owner sign-off | Not done | Requires continuation or rejection decision from the business owner |
| F1 achieved | Not done | Requires completed exit artifact from `docs/f1-pilot-validation-pack.md` |

## Why F1 Is Still Open

F1 is a real-world validation milestone, not an engineering feature milestone. The repository is ready for a pilot, but F1 cannot be marked complete until the evidence folder exists and contains sanitized proof of:

- real or near-real data;
- business-approved field mapping;
- approval integration behavior;
- metrics from a representative run set;
- trace-chain verification;
- compensation drill;
- business owner sign-off.

Local CSV runs, JSONL callback replay, generated reports, static console output, and additional sample-domain features do not close F1 by themselves.

## Next External Actions

1. Pick one pilot path: insurance claims-precheck, insurance renewal, or an internal process with equivalent data and approval semantics.
2. Open an F1 pilot validation issue using `.github/ISSUE_TEMPLATE/f1-pilot-validation.yml`.
3. Copy `docs/examples/f1-pilot-evidence-template/` into the approved internal evidence repository.
4. Fill `01-data-source.md` and `02-field-mapping.csv` before running the pilot.
5. Run the pilot against the approved data source and approval path.
6. Export metrics and trace evidence into `04-run-metrics.csv` and `05-trace-audit-export.jsonl`.
7. Record at least one compensation drill in `06-compensation-drill.md`.
8. Capture the business continuation decision in `07-business-signoff.md`.

Until these external actions are complete, the correct project status is **pilot-ready, F1 open**.
