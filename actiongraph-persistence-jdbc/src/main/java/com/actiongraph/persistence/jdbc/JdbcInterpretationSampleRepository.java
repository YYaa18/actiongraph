package com.actiongraph.persistence.jdbc;

import com.actiongraph.api.Experimental;
import com.actiongraph.exception.ActionGraphIntegrationException;
import com.actiongraph.interpretation.sampling.InterpretationSample;
import com.actiongraph.interpretation.sampling.InterpretationSampleRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JDBC-backed interpretation sample repository.
 */
@Experimental(
        since = "0.2.0",
        value = "Interpretation sampling persistence is experimental until STD3 pilots settle."
)
public final class JdbcInterpretationSampleRepository implements InterpretationSampleRepository {
    public static final String DEFAULT_TABLE = "actiongraph_interpretation_sample";

    private final DataSource dataSource;
    private final String table;

    public JdbcInterpretationSampleRepository(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE);
    }

    public JdbcInterpretationSampleRepository(DataSource dataSource, String table) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.table = JdbcTraceRepository.validateIdentifier(table);
        initializeSchema();
    }

    public void initializeSchema() {
        String sql = "create table if not exists " + table + " ("
                + "id varchar(128) primary key,"
                + "at varchar(64) not null,"
                + "masked_input clob not null,"
                + "outcome varchar(64) not null,"
                + "goal_type varchar(256) not null,"
                + "missing_fields clob not null,"
                + "fallback_used boolean not null,"
                + "parse_failure boolean not null,"
                + "run_id varchar(128),"
                + "labeled boolean not null"
                + ")";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot initialize interpretation sample repository schema", ex);
        }
    }

    @Override
    public void save(InterpretationSample sample) {
        Objects.requireNonNull(sample, "sample");
        String sql = "merge into " + table
                + " (id, at, masked_input, outcome, goal_type, missing_fields, fallback_used, parse_failure, run_id, labeled)"
                + " key(id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, sample);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot save interpretation sample", ex);
        }
    }

    @Override
    public List<InterpretationSample> findRecent(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        String sql = "select id, at, masked_input, outcome, goal_type, missing_fields, fallback_used, "
                + "parse_failure, run_id, labeled from " + table + " order by at desc fetch first ? rows only";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                java.util.ArrayList<InterpretationSample> samples = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    samples.add(read(resultSet));
                }
                return List.copyOf(samples);
            }
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot find recent interpretation samples", ex);
        }
    }

    @Override
    public Optional<InterpretationSample> findById(String id) {
        Objects.requireNonNull(id, "id");
        String sql = "select id, at, masked_input, outcome, goal_type, missing_fields, fallback_used, "
                + "parse_failure, run_id, labeled from " + table + " where id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(read(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot find interpretation sample: " + id, ex);
        }
    }

    @Override
    public void attachRunId(String id, String runId) {
        String sql = "update " + table + " set run_id = ? where id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            statement.setString(2, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot attach run id to interpretation sample: " + id, ex);
        }
    }

    @Override
    public void markLabeled(String id) {
        String sql = "update " + table + " set labeled = true where id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot mark interpretation sample labeled: " + id, ex);
        }
    }

    private static void bind(PreparedStatement statement, InterpretationSample sample) throws SQLException {
        statement.setString(1, sample.id());
        statement.setString(2, sample.at().toString());
        statement.setString(3, sample.maskedInput());
        statement.setString(4, sample.outcome());
        statement.setString(5, sample.goalType());
        statement.setString(6, String.join("\n", sample.missingFields().stream().sorted().toList()));
        statement.setBoolean(7, sample.fallbackUsed());
        statement.setBoolean(8, sample.parseFailure());
        statement.setString(9, sample.runId());
        statement.setBoolean(10, sample.labeled());
    }

    private static InterpretationSample read(ResultSet resultSet) throws SQLException {
        return new InterpretationSample(
                resultSet.getString("id"),
                Instant.parse(resultSet.getString("at")),
                resultSet.getString("masked_input"),
                resultSet.getString("outcome"),
                resultSet.getString("goal_type"),
                readMissingFields(resultSet.getString("missing_fields")),
                resultSet.getBoolean("fallback_used"),
                resultSet.getBoolean("parse_failure"),
                resultSet.getString("run_id"),
                resultSet.getBoolean("labeled")
        );
    }

    private static Set<String> readMissingFields(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split("\\R"))
                .filter(field -> !field.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
