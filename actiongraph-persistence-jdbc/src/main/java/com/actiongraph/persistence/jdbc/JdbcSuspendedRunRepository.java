package com.actiongraph.persistence.jdbc;

import com.actiongraph.action.ActionId;
import com.actiongraph.runtime.SuspendedRun;
import com.actiongraph.runtime.SuspendedRunRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class JdbcSuspendedRunRepository implements SuspendedRunRepository {
    public static final String DEFAULT_TABLE = "actiongraph_suspended_run";

    private final DataSource dataSource;
    private final PersistenceJsonCodec codec;
    private final String table;

    public JdbcSuspendedRunRepository(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE);
    }

    public JdbcSuspendedRunRepository(DataSource dataSource, String table) {
        this(dataSource, table, new PersistenceJsonCodec());
    }

    JdbcSuspendedRunRepository(DataSource dataSource, String table, PersistenceJsonCodec codec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.table = JdbcTraceRepository.validateIdentifier(table);
        this.codec = Objects.requireNonNull(codec, "codec");
        initializeSchema();
    }

    public void initializeSchema() {
        String sql = "create table if not exists " + table + " ("
                + "run_id varchar(128) primary key,"
                + "goal_json clob not null,"
                + "blackboard_json clob not null,"
                + "executed_actions_json clob not null,"
                + "compensation_stack_json clob not null,"
                + "pending_action_id varchar(256) not null,"
                + "message clob not null,"
                + "updated_at varchar(64) not null"
                + ")";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot initialize suspended run repository schema", ex);
        }
    }

    @Override
    public void save(SuspendedRun run) {
        Objects.requireNonNull(run, "run");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                delete(connection, run.runId());
                insert(connection, run);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot save suspended run: " + run.runId(), ex);
        }
    }

    @Override
    public Optional<SuspendedRun> findByRunId(String runId) {
        Objects.requireNonNull(runId, "runId");
        String sql = "select run_id, goal_json, blackboard_json, executed_actions_json, "
                + "compensation_stack_json, pending_action_id, message from " + table + " where run_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SuspendedRun(
                        resultSet.getString("run_id"),
                        codec.readGoal(resultSet.getString("goal_json")),
                        codec.readBlackboard(resultSet.getString("blackboard_json")),
                        codec.readActionIds(resultSet.getString("executed_actions_json")),
                        codec.readActionIds(resultSet.getString("compensation_stack_json")),
                        new ActionId(resultSet.getString("pending_action_id")),
                        resultSet.getString("message")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot find suspended run: " + runId, ex);
        }
    }

    @Override
    public void delete(String runId) {
        Objects.requireNonNull(runId, "runId");
        try (Connection connection = dataSource.getConnection()) {
            delete(connection, runId);
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot delete suspended run: " + runId, ex);
        }
    }

    private void delete(Connection connection, String runId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("delete from " + table + " where run_id = ?")) {
            statement.setString(1, runId);
            statement.executeUpdate();
        }
    }

    private void insert(Connection connection, SuspendedRun run) throws SQLException {
        String sql = "insert into " + table
                + " (run_id, goal_json, blackboard_json, executed_actions_json, compensation_stack_json, "
                + "pending_action_id, message, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, run.runId());
            statement.setString(2, codec.writeGoal(run.goal()));
            statement.setString(3, codec.writeBlackboard(run.blackboard()));
            statement.setString(4, codec.writeActionIds(run.executedActions()));
            statement.setString(5, codec.writeActionIds(run.compensationStack()));
            statement.setString(6, run.pendingActionId().value());
            statement.setString(7, run.message());
            statement.setString(8, Instant.now().toString());
            statement.executeUpdate();
        }
    }
}
