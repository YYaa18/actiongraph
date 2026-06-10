-- PostgreSQL dialect mapping for the claims precheck JDBC batch input.
-- Apply this to a reporting schema or adapt the source table reference to an
-- existing insurance core/read-replica table. ActionGraph should read only the
-- anonymized view below, never the source table.

CREATE SCHEMA IF NOT EXISTS actiongraph_claims;

-- Reference shape for the upstream business source. In a real deployment this
-- usually maps to existing claim, claimant, document, and approval tables.
CREATE TABLE IF NOT EXISTS actiongraph_claims.claims_precheck_source (
    source_claim_id TEXT PRIMARY KEY,
    policy_no TEXT NOT NULL,
    claimant_name TEXT NOT NULL,
    claimant_id_no TEXT NOT NULL,
    mobile_no TEXT NOT NULL,
    bank_card_no TEXT NOT NULL,
    claimed_amount NUMERIC(18, 2) NOT NULL,
    invoice_present BOOLEAN NOT NULL,
    closed BOOLEAN NOT NULL,
    approval_fails BOOLEAN NOT NULL,
    expected_intercept BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE OR REPLACE VIEW actiongraph_claims.claims_precheck_cases AS
SELECT
    concat('CLM-', upper(substr(md5(source_claim_id || ':actiongraph-claims-precheck-v1'), 1, 12))) AS claim_id,
    (round(claimed_amount / 1000, 0) * 1000)::NUMERIC(18, 2) AS claimed_amount,
    NOT invoice_present AS missing_invoice,
    closed,
    approval_fails,
    expected_intercept
FROM actiongraph_claims.claims_precheck_source
WHERE updated_at >= TIMESTAMPTZ '2026-01-01 00:00:00+08:00';

COMMENT ON VIEW actiongraph_claims.claims_precheck_cases IS
    'ActionGraph claims precheck batch input; excludes claimant PII and raw source identifiers.';

-- Operational grant pattern. Create the read-only role in the target database
-- and grant the view only; do not grant source-table access to ActionGraph.
-- REVOKE ALL ON actiongraph_claims.claims_precheck_source FROM actiongraph_reader;
-- GRANT SELECT ON actiongraph_claims.claims_precheck_cases TO actiongraph_reader;
