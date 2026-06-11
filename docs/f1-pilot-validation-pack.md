# F1 Pilot Validation Pack

F1 is not proven by adding more sample inputs, reports, or console screens. F1 is proven only when ActionGraph runs against a real or near-real business environment and produces evidence that a business owner wants to keep using the flow.

This pack turns that gate into a concrete checklist for the first insurance renewal or claims-precheck pilot.

## Entry Criteria

Use this pack only after the framework build is green and the claims-precheck sample is frozen. The pilot candidate must satisfy all of the following:

| Gate | Required evidence |
|---|---|
| Real or near-real data | A read-only data source, anonymized historical extract, reporting replica, or internal process dataset with production-like field shapes |
| Business owner | Named owner who can judge whether the generated draft and review handoff are useful |
| Approval path | Real approval callback source, shadow approval queue, or integration environment that exercises pending task query, decision, duplicate delivery, and resume |
| Data permission | Written confirmation that ActionGraph reads only approved views/extracts and does not access raw PII or transactional source tables |
| Rollback posture | Confirmation that the pilot creates drafts or shadow records only; no final payout, quote, adjustment, or customer-visible decision is made by the agent |

If any entry criterion is missing, the work remains pilot preparation, not F1 validation.

## Evidence Folder

Create one evidence folder per pilot run:

```text
f1-pilot-evidence/<pilot-id>/
  00-summary.md
  01-data-source.md
  02-field-mapping.csv
  03-approval-integration.md
  04-run-metrics.csv
  05-trace-audit-export.jsonl
  06-compensation-drill.md
  07-business-signoff.md
```

Do not commit customer data, secrets, raw identifiers, or screenshots with personal data. Store sensitive evidence in the approved internal evidence repository and commit only sanitized examples or templates.

Sanitized templates are available in `docs/examples/f1-pilot-evidence-template/`. Copy that directory for each pilot run, fill only sanitized fields, and keep sensitive attachments in the approved internal evidence repository.

## Field Mapping Worksheet

The data mapping must be reviewed before running the pilot.

| ActionGraph field | Source field/view | Transform | PII risk | Owner sign-off | Evidence |
|---|---|---|---|---|---|
| `claim_id` |  | hash/tokenize/raw business key | low/medium/high |  |  |
| `claimed_amount` |  | rounding/currency normalization | low/medium/high |  |  |
| `missing_invoice` |  | boolean rule | low/medium/high |  |  |
| `closed` |  | status mapping | low/medium/high |  |  |
| `approval_fails` |  | test-only or real failure marker | low/medium/high |  |  |

Minimum acceptance:

- Field mapping differences are documented.
- The pilot account has read-only access to the approved view/extract.
- The source contract can be replayed or sampled without exposing raw PII.
- JDBC URL, credentials, and tokens are redacted in all reports.

## Approval Integration Worksheet

The approval path is part of F1. A local JSONL replay is useful preparation, but it is not enough for validation.

| Case | Required result | Evidence |
|---|---|---|
| Pending task query | External approval side can find the suspended task with run id, action id, stage index, amount, risk, and trace metadata |  |
| Approve | `resume(runId)` continues from the same run id and creates exactly one downstream side effect |  |
| Deny | Run reaches terminal denial or compensation status and executed drafts are voided |  |
| Duplicate delivery | Duplicate approval callback is idempotent and does not create duplicate business side effects |  |
| Wrong stage | Stale or mismatched `expectedStageIndex` is rejected before resume |  |
| Bad token | Invalid callback token is rejected before state mutation |  |

Minimum acceptance:

- The approval system, gateway, or shadow adapter sends a real callback shape.
- Header authentication and retry behavior are documented.
- Duplicate, stale, and invalid messages have recorded outcomes.
- Resume race produces one business side effect, not two.

## Pilot Metrics

Collect the following for at least one representative batch:

| Metric | Source | Acceptance target |
|---|---|---|
| Total cases | Batch input or run list | Non-trivial sample agreed by business owner |
| Completion rate | Run terminal status | Explained by business rules, not framework errors |
| Intercept rate | Guard/policy terminal status | Intercepts are business-meaningful |
| Audit completeness | Trace chain verification | 100% for accepted pilot evidence |
| Average runtime | Batch report / console export | Split business action time, framework time, and approval wait |
| Compensation drill | Deny/failure case | Executed drafts are voided and trace contains compensation events |
| Human review wait | Approval task timestamps | Derived from real or shadow approval timing |

## Go / No-Go

F1 is considered achieved only when all of these are true:

- A real or near-real data source has been used.
- A real or shadow approval integration has exercised pending, approve/deny, duplicate, stale-stage, and invalid-token paths.
- The business owner confirms the generated draft or precheck result is useful enough to continue.
- Audit export and trace-chain verification are complete for the accepted run set.
- The remaining gaps are integration or operations work, not missing framework semantics.

F1 is not achieved when the only evidence is a local CSV, JSONL replay, generated report, static console, or sample-domain feature.

## Allowed Changes During Freeze

Claims-precheck sample changes are allowed only for:

- fixing defects found by real or near-real validation;
- adapting to an approved data source shape or approval callback shape;
- adding sanitized evidence templates;
- improving redaction of credentials, source identifiers, or customer data;
- documenting pilot findings.

Changes that add new reports, new simulated approval modes, new demo-only data sources, new console views, or new sample flows must wait until after pilot feedback.

## Exit Artifact

The final F1 package should contain:

- `00-summary.md` with pilot scope, environment, owner, date, and go/no-go decision;
- sanitized field mapping;
- sanitized approval integration notes;
- metrics CSV;
- audit export sample;
- compensation drill record;
- business sign-off or explicit rejection reason.

Without the exit artifact, F1 remains open.
