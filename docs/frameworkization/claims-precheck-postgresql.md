# Claims Precheck PostgreSQL Mapping

F1 includes a PostgreSQL dialect contract for connecting the claims precheck batch runner to a real or near-real insurance data source.

## Files

- `actiongraph-samples/src/main/resources/sql/claims-precheck-source-contract.sql`: H2-compatible executable contract used by automated tests.
- `actiongraph-samples/src/main/resources/sql/postgresql/claims-precheck-source-contract.sql`: PostgreSQL dialect mapping for a reporting schema or read replica.

## Contract

ActionGraph reads only this view shape:

```sql
SELECT claim_id, claimed_amount, missing_invoice, closed, approval_fails, expected_intercept
FROM actiongraph_claims.claims_precheck_cases
ORDER BY claim_id;
```

The PostgreSQL view intentionally excludes claimant names, ID numbers, mobile numbers, bank card numbers, policy numbers, and raw source claim identifiers. `claim_id` is a stable token derived from the source claim id, and amounts are rounded to CNY 1,000 buckets before leaving the source schema.

## Local Runner Command

Use the qualified query when the database does not put `actiongraph_claims` on the connection `search_path`:

```bash
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics \
  --args='--jdbc-url jdbc:postgresql://db.example/claims \
  --jdbc-user actiongraph_reader \
  --jdbc-query "SELECT claim_id, claimed_amount, missing_invoice, closed, approval_fails, expected_intercept FROM actiongraph_claims.claims_precheck_cases ORDER BY claim_id" \
  --report-dir actiongraph-samples/build/reports/claims-precheck \
  --batch-id F1-CLAIMS-PG-001 \
  --environment staging'
```

Add the PostgreSQL JDBC driver to the sample runtime classpath before running against a real PostgreSQL database.

## Review Checklist

- Grant the ActionGraph runtime user `SELECT` on `actiongraph_claims.claims_precheck_cases` only.
- Do not grant source-table access to the runtime user.
- Keep the six output column names stable, or pass an equivalent `--jdbc-query` that aliases them back to the contract names.
- Re-run `./gradlew :actiongraph-samples:test --tests '*ClaimsPrecheck*'` after adapting the contract.
