# Compensation Drill Evidence

> Sanitized template. Use shadow records or approved draft records only.

## Drill Scope

| Field | Value |
|---|---|
| Run ID | `<sanitized-run-id>` |
| Scenario | denied review / service failure / policy rejection |
| Draft object | `<sanitized-draft-id>` |
| Trigger | deny / exception / policy |
| Terminal status | DENIED_BY_POLICY / FAILED_COMPENSATED / FAILED_COMPENSATION_INCOMPLETE |
| Evidence location | `<internal reference>` |

## Required Evidence

- [ ] Draft or shadow record existed before compensation
- [ ] Compensation action was invoked
- [ ] Draft or shadow record was voided/reversed
- [ ] Trace contains `COMPENSATED` event
- [ ] If compensation failed, terminal status and residual side effect are documented

## Notes

- Compensation action:
- Side effect before:
- Side effect after:
- Trace export reference:
