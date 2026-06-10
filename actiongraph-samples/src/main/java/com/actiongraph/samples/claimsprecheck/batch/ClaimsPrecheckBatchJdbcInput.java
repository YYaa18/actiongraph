package com.actiongraph.samples.claimsprecheck.batch;

import java.util.regex.Pattern;

public record ClaimsPrecheckBatchJdbcInput(
        String url,
        String user,
        String password,
        String query
) {
    public static final String DEFAULT_QUERY = """
            SELECT claim_id, claimed_amount, missing_invoice, closed, approval_fails, expected_intercept
            FROM claims_precheck_cases
            ORDER BY claim_id
            """;

    private static final Pattern PASSWORD_PARAMETER = Pattern.compile("(?i)((?:password|pwd)\\s*=)[^;&?]+");

    public ClaimsPrecheckBatchJdbcInput {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        user = user == null ? "" : user;
        password = password == null ? "" : password;
        query = query == null || query.isBlank() ? DEFAULT_QUERY : query;
    }

    public String sourceDescription() {
        return PASSWORD_PARAMETER.matcher(url).replaceAll("$1***");
    }
}
