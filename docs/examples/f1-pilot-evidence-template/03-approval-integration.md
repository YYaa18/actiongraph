# Approval Integration Evidence

> Sanitized template. Do not commit callback secrets, raw tokens, or personal reviewer data.

## Integration Overview

| Field | Value |
|---|---|
| Approval source | real system / shadow queue / gateway adapter |
| Callback path | HTTP callback / message replay / manual decision adapter |
| Authentication | shared secret / gateway auth / internal IAM |
| Retry behavior | documented / not documented |
| Stage model | single-stage / multi-stage |
| Evidence location | `<internal reference>` |

## Required Cases

| Case | Run ID | Expected result | Actual result | Evidence |
|---|---|---|---|---|
| Pending task query | `<sanitized-run-id>` | Task visible with run id, action id, stage index, amount, risk, trace metadata |  |  |
| Approve | `<sanitized-run-id>` | Resume continues same run id and creates exactly one downstream side effect |  |  |
| Deny | `<sanitized-run-id>` | Terminal denial or compensation status; executed drafts voided |  |  |
| Duplicate delivery | `<sanitized-run-id>` | Duplicate callback is idempotent; no duplicate side effects |  |  |
| Wrong stage | `<sanitized-run-id>` | Stale or mismatched expectedStageIndex rejected before resume |  |  |
| Bad token | `<sanitized-run-id>` | Invalid token rejected before state mutation |  |  |

## Notes

- Header names:
- Token redaction method:
- Retry interval:
- Idempotency key:
- Known integration gaps:
