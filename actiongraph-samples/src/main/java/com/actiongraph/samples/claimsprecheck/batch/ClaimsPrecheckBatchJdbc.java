package com.actiongraph.samples.claimsprecheck.batch;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class ClaimsPrecheckBatchJdbc {
    private ClaimsPrecheckBatchJdbc() {
    }

    public static List<ClaimsPrecheckBatchCase> readCases(ClaimsPrecheckBatchJdbcInput input) {
        Objects.requireNonNull(input, "input");
        try (Connection connection = DriverManager.getConnection(input.url(), connectionProperties(input));
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(input.query())) {
            Map<String, Integer> columns = columnsByName(resultSet.getMetaData());
            List<ClaimsPrecheckBatchCase> cases = new ArrayList<>();
            while (resultSet.next()) {
                cases.add(new ClaimsPrecheckBatchCase(
                        readString(resultSet, columns, "claimId", "claim_id"),
                        readBigDecimal(resultSet, columns, "claimedAmount", "claimed_amount"),
                        readBoolean(resultSet, columns, "missingInvoice", "missing_invoice"),
                        readBoolean(resultSet, columns, "closed"),
                        readBoolean(resultSet, columns, "approvalFails", "approval_fails"),
                        readBoolean(resultSet, columns, "expectedIntercept", "expected_intercept")
                ));
            }
            return List.copyOf(cases);
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot read claims precheck cases from JDBC source", ex);
        }
    }

    private static Properties connectionProperties(ClaimsPrecheckBatchJdbcInput input) {
        Properties properties = new Properties();
        if (!input.user().isBlank()) {
            properties.setProperty("user", input.user());
        }
        if (!input.password().isBlank()) {
            properties.setProperty("password", input.password());
        }
        return properties;
    }

    private static Map<String, Integer> columnsByName(ResultSetMetaData metadata) throws SQLException {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            columns.put(normalize(metadata.getColumnLabel(i)), i);
        }
        return columns;
    }

    private static String readString(ResultSet resultSet, Map<String, Integer> columns, String... names)
            throws SQLException {
        String value = resultSet.getString(columnIndex(columns, names));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing JDBC column value for " + names[0]);
        }
        return value;
    }

    private static BigDecimal readBigDecimal(ResultSet resultSet, Map<String, Integer> columns, String... names)
            throws SQLException {
        BigDecimal value = resultSet.getBigDecimal(columnIndex(columns, names));
        if (value == null) {
            throw new IllegalStateException("Missing JDBC column value for " + names[0]);
        }
        return value;
    }

    private static boolean readBoolean(ResultSet resultSet, Map<String, Integer> columns, String... names)
            throws SQLException {
        Object value = resultSet.getObject(columnIndex(columns, names));
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString().trim());
    }

    private static int columnIndex(Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(normalize(name));
            if (index != null) {
                return index;
            }
        }
        throw new IllegalStateException("Missing JDBC result column: " + String.join("/", names));
    }

    private static String normalize(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }
}
