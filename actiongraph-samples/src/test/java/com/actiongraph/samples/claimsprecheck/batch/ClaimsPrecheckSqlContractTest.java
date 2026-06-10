package com.actiongraph.samples.claimsprecheck.batch;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimsPrecheckSqlContractTest {
    @Test
    void postgresqlDialectExposesOnlyTheBatchViewColumns() throws Exception {
        String sql = readResource("/sql/postgresql/claims-precheck-source-contract.sql");
        String viewBody = viewBody(sql);

        assertThat(sql)
                .contains("CREATE OR REPLACE VIEW actiongraph_claims.claims_precheck_cases")
                .contains("GRANT SELECT ON actiongraph_claims.claims_precheck_cases TO actiongraph_reader");
        assertThat(viewBody)
                .contains(" as claim_id")
                .contains("md5(source_claim_id")
                .contains(" as claimed_amount")
                .contains(" as missing_invoice")
                .contains("closed")
                .contains("approval_fails")
                .contains("expected_intercept")
                .doesNotContain("claimant_name")
                .doesNotContain("claimant_id_no")
                .doesNotContain("mobile_no")
                .doesNotContain("bank_card_no")
                .doesNotContain("policy_no")
                .doesNotContain(" as source_claim_id")
                .doesNotContain("row_number()");
    }

    @Test
    void postgresqlDialectDocumentsTheSameReaderColumnsAsTheJdbcDefaultQuery() throws Exception {
        String sql = readResource("/sql/postgresql/claims-precheck-source-contract.sql");
        String defaultQuery = ClaimsPrecheckBatchJdbcInput.DEFAULT_QUERY.toLowerCase(Locale.ROOT);

        assertThat(viewBody(sql))
                .contains(" as claim_id")
                .contains(" as claimed_amount")
                .contains(" as missing_invoice")
                .contains("closed")
                .contains("approval_fails")
                .contains("expected_intercept");
        assertThat(defaultQuery)
                .contains("claim_id")
                .contains("claimed_amount")
                .contains("missing_invoice")
                .contains("closed")
                .contains("approval_fails")
                .contains("expected_intercept");
    }

    private static String readResource(String resource) throws Exception {
        Path path = Path.of(Objects.requireNonNull(
                ClaimsPrecheckSqlContractTest.class.getResource(resource),
                resource
        ).toURI());
        return Files.readString(path);
    }

    private static String viewBody(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        int createView = lower.indexOf("create or replace view actiongraph_claims.claims_precheck_cases");
        int comment = lower.indexOf("comment on view actiongraph_claims.claims_precheck_cases");
        assertThat(createView).isGreaterThanOrEqualTo(0);
        assertThat(comment).isGreaterThan(createView);
        return lower.substring(createView, comment);
    }
}
