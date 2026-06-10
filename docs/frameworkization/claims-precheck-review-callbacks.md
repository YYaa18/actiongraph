# Claims Precheck Review Callback Replay

F1 includes a JSONL callback replay fixture for testing how an external approval system feeds decisions back into ActionGraph.

## Files

- `actiongraph-samples/src/main/resources/claims-precheck-review-callbacks.jsonl`
- `actiongraph-samples/src/main/java/com/actiongraph/samples/claimsprecheck/batch/ClaimsPrecheckReviewCallbackReplayer.java`

The replayer uses `HumanReviewCallbackHandler` from `actiongraph-human-review`; it does not bypass the repository or resume logic.

## Message Shape

Each non-empty, non-comment JSONL line is one callback delivery:

```json
{
  "deliveryId": "cb-100",
  "claimId": "CLM100",
  "runId": "$RUN_ID",
  "actionId": "claim.approval.request",
  "expectedStageIndex": 0,
  "decision": "APPROVED",
  "reviewer": "claims-checker",
  "comment": "approved",
  "decisionDelayMs": 5,
  "token": "review-secret"
}
```

`runId` may be the actual suspended run id, `$RUN_ID`, `${runId}`, or blank. The replay sample resolves placeholders from the pending task so fixture files remain deterministic even though run ids are generated at runtime.

## Local Command

```bash
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics \
  --args='--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv \
  --review-callbacks actiongraph-samples/src/main/resources/claims-precheck-review-callbacks.jsonl \
  --review-callback-secret review-secret \
  --report-dir actiongraph-samples/build/reports/claims-precheck \
  --batch-id F1-CLAIMS-CALLBACKS \
  --environment local'
```

`--review-callback-secret` can also come from `ACTIONGRAPH_REVIEW_CALLBACK_SECRET`.

## Semantics Covered

- Authentication: callback tokens must match the configured shared secret before the repository is mutated.
- Stage CAS: callbacks use `expectedStageIndex`, so stale approval pages cannot decide the next stage accidentally.
- Idempotent retry: a duplicate delivery with the same stage decision is accepted as already applied.
- Conflict detection: a duplicate delivery with a different final decision/reviewer/comment fails fast.
- Resume continuity: after callback handling, the batch runner resumes the same suspended run through `GoapExecutor.resume(...)`.

For Spring applications, add `actiongraph-human-review-spring-boot-starter` plus `actiongraph-human-review-callback-spring-boot-starter`; the `runId`, `actionId`, `expectedStageIndex`, `decision`, `reviewer`, and `comment` fields map to the callback endpoint at `/actiongraph/human-review/callbacks`. Send the shared secret through the configured token header, not as a JSON body field.
