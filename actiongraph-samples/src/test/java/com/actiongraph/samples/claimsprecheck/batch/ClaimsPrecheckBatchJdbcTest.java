package com.actiongraph.samples.claimsprecheck.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimsPrecheckBatchJdbcTest {
    @TempDir
    Path tempDir;

    @Test
    void readsBatchCasesFromJdbcResultSet() throws Exception {
        String url = seededDatabase();

        var input = new ClaimsPrecheckBatchJdbcInput(url, "", "", null);

        var cases = ClaimsPrecheckBatchJdbc.readCases(input);

        assertThat(cases)
                .extracting(ClaimsPrecheckBatchCase::claimId)
                .containsExactly("CLM200", "CLM201", "CLM202");
        assertThat(cases.get(1).missingInvoice()).isTrue();
        assertThat(cases.get(2).claimedAmount()).isEqualByComparingTo("1200000");
        assertThat(cases.get(2).expectedIntercept()).isTrue();

        ClaimsPrecheckBatchMetrics metrics = new ClaimsPrecheckBatchRunner().run(cases);
        assertThat(metrics.totalRuns()).isEqualTo(3);
        assertThat(metrics.completedRuns()).isEqualTo(1);
        assertThat(metrics.interceptedRuns()).isEqualTo(2);
        assertThat(metrics.auditCompletenessRate()).isEqualTo(100.0);
    }

    @Test
    void appLoadsCasesFromJdbcAndWritesReport() throws Exception {
        String url = seededDatabase();

        ClaimsPrecheckBatchMetricsApp.main(new String[] {
                "--jdbc-url", url,
                "--report-dir", tempDir.toString(),
                "--batch-id", "F1-CLAIMS-JDBC-TEST",
                "--environment", "test"
        });

        String report = Files.readString(tempDir.resolve(ClaimsPrecheckBatchReportWriter.MARKDOWN_REPORT));
        assertThat(report)
                .contains("Batch ID: F1-CLAIMS-JDBC-TEST")
                .contains("Environment: test")
                .contains("Sample Source: " + url)
                .contains("Total Runs: 3")
                .contains("Intercept Rate: 66.67%")
                .contains("Audit Completeness Rate: 100.00%");
    }

    @Test
    void masksPasswordParameterInSourceDescription() {
        var input = new ClaimsPrecheckBatchJdbcInput(
                "jdbc:postgresql://db.example/claims?user=demo&password=secret",
                "",
                "",
                "select 1"
        );

        assertThat(input.sourceDescription())
                .isEqualTo("jdbc:postgresql://db.example/claims?user=demo&password=***");
    }

    private String seededDatabase() throws Exception {
        String url = "jdbc:h2:mem:claims_precheck_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE claims_precheck_cases (
                        claim_id VARCHAR(32) PRIMARY KEY,
                        claimed_amount DECIMAL(18, 2) NOT NULL,
                        missing_invoice BOOLEAN NOT NULL,
                        closed BOOLEAN NOT NULL,
                        approval_fails BOOLEAN NOT NULL,
                        expected_intercept BOOLEAN NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO claims_precheck_cases
                    (claim_id, claimed_amount, missing_invoice, closed, approval_fails, expected_intercept)
                    VALUES
                    ('CLM200', 260000, FALSE, FALSE, FALSE, FALSE),
                    ('CLM201', 180000, TRUE, FALSE, FALSE, TRUE),
                    ('CLM202', 1200000, FALSE, FALSE, FALSE, TRUE)
                    """);
        }
        return url;
    }
}
