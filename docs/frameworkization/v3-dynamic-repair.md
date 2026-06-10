# v3 Dynamic Repair Planning

v3 dynamic repair is now demonstrated without changing `GoapPlanner` or `GoapExecutor`.

## Pattern

The runtime can repair a blocked preferred path when an action fails its runtime guard:

1. The planner selects the normal path from symbolic conditions.
2. The executor evaluates the next action's runtime guard.
3. If the guard fails, that action is excluded for the current selection loop.
4. The planner replans from the same Blackboard state.
5. An alternative action with compatible effects can satisfy the same downstream preconditions.

This keeps the planner symbolic and deterministic while letting runtime facts steer execution.

## Renewal Example

Normal path:

```text
contract.current.query -> renewal.eligibility.check -> quote.draft.create -> sales.approval.request
```

Repair path when no current contract exists:

```text
contract.current.query guard fails
contract.synthetic.create -> renewal.eligibility.check -> quote.draft.create -> sales.approval.request
```

The fallback action:

- `contract.synthetic.create`
- precondition: `renewal:CUSTOMER_ID_PRESENT`
- effect: `renewal:CURRENT_CONTRACT_LOADED`

It produces the same downstream condition as `contract.current.query`, so the rest of the plan is unchanged.

## Verification

`RenewalQuoteFlowTest.missingCurrentContractReplansToSyntheticContractAction` proves:

- the initial plan includes `contract.current.query`
- `contract.current.query` is blocked by `RUNTIME_GUARD_FAILED`
- the replan includes and executes `contract.synthetic.create`
- the overall run still reaches `COMPLETED`

No core planner or executor behavior was changed for this.
