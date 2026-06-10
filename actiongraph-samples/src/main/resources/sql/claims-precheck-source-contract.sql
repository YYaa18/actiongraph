-- Reference data contract for the claims precheck JDBC batch input.
-- Business systems can expose this shape as a table or view named claims_precheck_cases.
-- The source table below is deliberately richer than the exported view to show the
-- anonymization boundary: identifiers and personal fields stay outside ActionGraph.

DROP VIEW IF EXISTS claims_precheck_cases;
DROP TABLE IF EXISTS claims_precheck_source;

CREATE TABLE claims_precheck_source (
    source_claim_id VARCHAR(64) PRIMARY KEY,
    claimant_name VARCHAR(128) NOT NULL,
    claimant_id_no VARCHAR(64) NOT NULL,
    claimed_amount DECIMAL(18, 2) NOT NULL,
    invoice_present BOOLEAN NOT NULL,
    closed BOOLEAN NOT NULL,
    approval_fails BOOLEAN NOT NULL,
    expected_intercept BOOLEAN NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO claims_precheck_source
(source_claim_id, claimant_name, claimant_id_no, claimed_amount, invoice_present,
 closed, approval_fails, expected_intercept, updated_at)
VALUES
('REAL-CLAIM-2026-0001', 'Synthetic Person 1', 'SYNTHETIC-ID-001', 260123.45, TRUE, FALSE, FALSE, FALSE, TIMESTAMP '2026-06-01 10:00:00'),
('REAL-CLAIM-2026-0002', 'Synthetic Person 2', 'SYNTHETIC-ID-002', 180456.00, FALSE, FALSE, FALSE, TRUE, TIMESTAMP '2026-06-01 10:05:00'),
('REAL-CLAIM-2026-0003', 'Synthetic Person 3', 'SYNTHETIC-ID-003', 90234.56, TRUE, TRUE, FALSE, TRUE, TIMESTAMP '2026-06-01 10:10:00'),
('REAL-CLAIM-2026-0004', 'Synthetic Person 4', 'SYNTHETIC-ID-004', 1200400.00, TRUE, FALSE, FALSE, TRUE, TIMESTAMP '2026-06-01 10:15:00'),
('REAL-CLAIM-2026-0005', 'Synthetic Person 5', 'SYNTHETIC-ID-005', 220321.00, TRUE, FALSE, TRUE, FALSE, TIMESTAMP '2026-06-01 10:20:00');

CREATE VIEW claims_precheck_cases AS
SELECT
    'CLM' || CAST(900 + ROW_NUMBER() OVER (ORDER BY source_claim_id) AS VARCHAR) AS claim_id,
    CAST(ROUND(claimed_amount / 1000, 0) * 1000 AS DECIMAL(18, 2)) AS claimed_amount,
    NOT invoice_present AS missing_invoice,
    closed,
    approval_fails,
    expected_intercept
FROM claims_precheck_source
WHERE updated_at >= TIMESTAMP '2026-01-01 00:00:00';
