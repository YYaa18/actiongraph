# Goal Configuration and Bundles

Status: Packaging, Experimental in `0.2.x`.

Configuration-defined Goals let a deployment add a new business capability without
shipping Java code, as long as the target conditions are already reachable from
registered Actions. The runtime still uses the same symbolic planner, policy
guards, human review, compensation, trace, and persistence semantics.

## Inline configuration

Spring Boot applications can register external Goal definitions under
`actiongraph.goals.definitions`:

```yaml
actiongraph:
  goals:
    definitions:
      - type: product.reprice
        description: Reprice an existing product from a promoted business rule.
        target-conditions:
          - product:PRICE_UPDATED
        seed-conditions:
          - product:PRODUCT_ID_PRESENT
          - product:PRICE_PRESENT
        parameters:
          - name: productId
            type: long
            description: Product id
            example: "1001"
          - name: price
            type: decimal
            description: New price
            example: "199.00"
```

Configured parameters are seeded into the Blackboard using their parameter name
as the Blackboard key. The value type is resolved at startup:

- built-ins: `string`, `int`/`integer`, `long`, `decimal`/`bigdecimal`,
  `double`, `boolean`/`bool`;
- fully qualified Java class names when a matching `TypedGoalValueConverter`
  bean is registered.

Unknown types fail startup. This is deliberate: a promoted Goal must never wait
until the first user request to discover that its parameter binding is invalid.

## Source precedence

Goal type uniqueness is global. Startup fails when two sources declare the same
Goal type and reports both sources. Sources are merged in this order:

1. `GoalDefinition` beans
2. `ActionGraphContribution` Goals and annotated beans
3. `@ActionGraphGoal` Spring beans
4. `actiongraph.goals.definitions`
5. imported Goal bundles

This is not override precedence. Duplicates are configuration errors.

## Validation and fingerprints

Configured Goals participate in the normal startup graph validation. A Goal is
accepted only when its target conditions are reachable from the declared seed
conditions and the registered Action graph.

Every `RUN_STARTED` trace includes:

- `goalFingerprint`
- `actionGraphFingerprint`

The Goal fingerprint covers the Goal type, description, target conditions,
seed conditions, schema metadata, parameter definitions, and parameter-seeding
mode. The Action graph fingerprint covers Action ids, preconditions, effects,
risk level, and human-review requirement.

## Bundle import

Goal Studio exports the same definition shape inside a bundle:

```yaml
actiongraph-bundle:
  bundle-fingerprint: "<sha256 of definitions>"
  action-graph-fingerprint: "<sha256 of the validated Action graph>"
  validated-at: "2026-06-12T00:00:00Z"
  approved-by: "tester"
  source-env: "test"
  definitions:
    - type: product.reprice
      description: Reprice an existing product.
      target-conditions:
        - product:PRICE_UPDATED
      seed-conditions:
        - product:PRODUCT_ID_PRESENT
      parameters:
        - name: productId
          type: long
```

Production imports bundles with:

```yaml
actiongraph:
  goals:
    bundle:
      locations:
        - file:/opt/actiongraph/goals/product-reprice.yml
      fingerprint-mismatch: FAIL
```

Import checks happen before the application is considered ready:

- the bundle fingerprint must match the definitions in the file;
- the saved Action graph fingerprint must match the current graph by default;
- `fingerprint-mismatch: WARN` allows a deliberate rollout when the graph drift
  is accepted, but startup validation still re-checks reachability.

Runtime hot loading is intentionally out of scope. Restarting to promote a
bundle keeps the change inside normal deployment and audit controls.
