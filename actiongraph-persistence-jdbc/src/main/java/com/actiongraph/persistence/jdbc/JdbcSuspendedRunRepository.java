package com.actiongraph.persistence.jdbc;

import com.actiongraph.action.ActionId;
import com.actiongraph.runtime.SuspendedRun;
import com.actiongraph.runtime.SuspendedRunRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class JdbcSuspendedRunRepository implements SuspendedRunRepository {
    public static final String DEFAULT_TABLE = "actiongraph_suspended_run";
    public static final Duration DEFAULT_CLAIM_TIMEOUT = Duration.ofMinutes(15);
    public static final int SNAPSHOT_FORMAT_VERSION = 1;
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String STATUS_RESUMING = "RESUMING";

    private final DataSource dataSource;
    private final PersistenceJsonCodec codec;
    private final String table;
    private final Duration claimTimeout;

    public JdbcSuspendedRunRepository(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE);
    }

    public JdbcSuspendedRunRepository(DataSource dataSource, BlackboardTypeRegistry blackboardTypeRegistry) {
        this(dataSource, DEFAULT_TABLE, DEFAULT_CLAIM_TIMEOUT, blackboardTypeRegistry);
    }

    public JdbcSuspendedRunRepository(DataSource dataSource, Duration claimTimeout) {
        this(dataSource, DEFAULT_TABLE, claimTimeout);
    }

    public JdbcSuspendedRunRepository(
            DataSource dataSource,
            Duration claimTimeout,
            BlackboardTypeRegistry blackboardTypeRegistry
    ) {
        this(dataSource, DEFAULT_TABLE, claimTimeout, blackboardTypeRegistry);
    }

    public JdbcSuspendedRunRepository(DataSource dataSource, String table) {
        this(dataSource, table, DEFAULT_CLAIM_TIMEOUT);
    }

    public JdbcSuspendedRunRepository(
            DataSource dataSource,
            String table,
            BlackboardTypeRegistry blackboardTypeRegistry
    ) {
        this(dataSource, table, DEFAULT_CLAIM_TIMEOUT, blackboardTypeRegistry);
    }

    public JdbcSuspendedRunRepository(DataSource dataSource, String table, Duration claimTimeout) {
        this(dataSource, table, claimTimeout, new PersistenceJsonCodec());
    }

    public JdbcSuspendedRunRepository(
            DataSource dataSource,
            String table,
            Duration claimTimeout,
            BlackboardTypeRegistry blackboardTypeRegistry
    ) {
        this(dataSource, table, claimTimeout, new PersistenceJsonCodec(blackboardTypeRegistry));
    }

    JdbcSuspendedRunRepository(DataSource dataSource, String table, PersistenceJsonCodec codec) {
        this(dataSource, table, DEFAULT_CLAIM_TIMEOUT, codec);
    }

    JdbcSuspendedRunRepository(
            DataSource dataSource,
            String table,
            Duration claimTimeout,
            PersistenceJsonCodec codec
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.table = JdbcTraceRepository.validateIdentifier(table);
        this.claimTimeout = validateClaimTimeout(claimTimeout);
        this.codec = Objects.requireNonNull(codec, "codec");
        initializeSchema();
    }

    public void initializeSchema() {
        String sql = "create table if not exists " + table + " ("
                + "run_id varchar(128) primary key,"
                + "snapshot_version int not null,"
                + "goal_json clob not null,"
                + "blackboard_json clob not null,"
                + "executed_actions_json clob not null,"
                + "compensation_stack_json clob not null,"
                + "pending_action_id varchar(256) not null,"
                + "message clob not null,"
                + "status varchar(32) not null,"
                + "claimed_at varchar(64),"
                + "updated_at varchar(64) not null"
                + ")";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            ensureColumn(connection, "snapshot_version", "int");
            ensureColumn(connection, "status", "varchar(32)");
            ensureColumn(connection, "claimed_at", "varchar(64)");
            try (Statement update = connection.createStatement()) {
                update.executeUpdate("update " + table + " set snapshot_version = " + SNAPSHOT_FORMAT_VERSION
                        + " where snapshot_version is null");
                update.executeUpdate("update " + table + " set status = '" + STATUS_SUSPENDED
                        + "' where status is null");
            }
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
        try (Connection connection = dataSource.getConnection()) {
            return findByRunId(connection, runId);
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot find suspended run: " + runId, ex);
        }
    }

    @Override
    public Optional<SuspendedRun> claimForResume(String runId) {
        Objects.requireNonNull(runId, "runId");
        Instant now = Instant.now();
        Instant staleBefore = now.minus(claimTimeout);
        String sql = "update " + table + " set status = ?, claimed_at = ?, updated_at = ? "
                + "where run_id = ? and (status is null or status = ? or (status = ? and claimed_at < ?))";
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, STATUS_RESUMING);
                statement.setString(2, now.toString());
                statement.setString(3, now.toString());
                statement.setString(4, runId);
                statement.setString(5, STATUS_SUSPENDED);
                statement.setString(6, STATUS_RESUMING);
                statement.setString(7, staleBefore.toString());
                int claimed = statement.executeUpdate();
                if (claimed == 0) {
                    connection.commit();
                    return Optional.empty();
                }
                Optional<SuspendedRun> run = findByRunId(connection, runId);
                connection.commit();
                return run;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot claim suspended run for resume: " + runId, ex);
        }
    }

    private Optional<SuspendedRun> findByRunId(Connection connection, String runId) throws SQLException {
        String sql = "select run_id, snapshot_version, goal_json, blackboard_json, executed_actions_json, "
                + "compensation_stack_json, pending_action_id, message from " + table + " where run_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                verifySnapshotVersion(readSnapshotVersion(resultSet));
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
                + " (run_id, snapshot_version, goal_json, blackboard_json, executed_actions_json, compensation_stack_json, "
                + "pending_action_id, message, status, claimed_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, run.runId());
            statement.setInt(2, SNAPSHOT_FORMAT_VERSION);
            statement.setString(3, codec.writeGoal(run.goal()));
            statement.setString(4, codec.writeBlackboard(run.blackboard()));
            statement.setString(5, codec.writeActionIds(run.executedActions()));
            statement.setString(6, codec.writeActionIds(run.compensationStack()));
            statement.setString(7, run.pendingActionId().value());
            statement.setString(8, run.message());
            statement.setString(9, STATUS_SUSPENDED);
            statement.setNull(10, Types.VARCHAR);
            statement.setString(11, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private int readSnapshotVersion(ResultSet resultSet) throws SQLException {
        int snapshotVersion = resultSet.getInt("snapshot_version");
        return resultSet.wasNull() ? SNAPSHOT_FORMAT_VERSION : snapshotVersion;
    }

    private void verifySnapshotVersion(int snapshotVersion) {
        if (snapshotVersion != SNAPSHOT_FORMAT_VERSION) {
            throw new UnsupportedSuspendedRunSnapshotVersionException(snapshotVersion, SNAPSHOT_FORMAT_VERSION);
        }
    }

    private void ensureColumn(Connection connection, String column, String definition) throws SQLException {
        if (columnExists(connection, column)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table " + table + " add column " + column + " " + definition);
        }
    }

    private boolean columnExists(Connection connection, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String requested = column.toLowerCase(Locale.ROOT);
        try (ResultSet resultSet = metaData.getColumns(null, null, table, null)) {
            while (resultSet.next()) {
                if (requested.equals(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        try (ResultSet resultSet = metaData.getColumns(null, null, table.toUpperCase(Locale.ROOT), null)) {
            while (resultSet.next()) {
                if (requested.equals(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Duration validateClaimTimeout(Duration claimTimeout) {
        Duration value = Objects.requireNonNull(claimTimeout, "claimTimeout");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("claimTimeout must be positive");
        }
        return value;
    }
}
