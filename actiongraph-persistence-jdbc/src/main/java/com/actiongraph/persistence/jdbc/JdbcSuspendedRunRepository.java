package com.actiongraph.persistence.jdbc;

import com.actiongraph.action.ActionId;
import com.actiongraph.exception.ActionGraphIntegrationException;
import com.actiongraph.runtime.SnapshotState;
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
                + "snapshot_state varchar(16),"
                + "heartbeat_at varchar(64),"
                + "in_flight_action_id varchar(256),"
                + "event_type varchar(256),"
                + "event_correlation_id varchar(512),"
                + "event_deadline varchar(64),"
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
            ensureColumn(connection, "snapshot_state", "varchar(16)");
            ensureColumn(connection, "heartbeat_at", "varchar(64)");
            ensureColumn(connection, "in_flight_action_id", "varchar(256)");
            ensureColumn(connection, "event_type", "varchar(256)");
            ensureColumn(connection, "event_correlation_id", "varchar(512)");
            ensureColumn(connection, "event_deadline", "varchar(64)");
            try (Statement update = connection.createStatement()) {
                update.executeUpdate("update " + table + " set snapshot_version = " + SNAPSHOT_FORMAT_VERSION
                        + " where snapshot_version is null");
                update.executeUpdate("update " + table + " set status = '" + STATUS_SUSPENDED
                        + "' where status is null");
                update.executeUpdate("update " + table + " set snapshot_state = '" + SnapshotState.SUSPENDED.name()
                        + "' where snapshot_state is null");
                update.executeUpdate("update " + table + " set heartbeat_at = updated_at where heartbeat_at is null");
            }
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot initialize suspended run repository schema", ex);
        }
    }

    @Override
    public void save(SuspendedRun run) {
        Objects.requireNonNull(run, "run");
        if (run.snapshotState() != SnapshotState.SUSPENDED
                && run.snapshotState() != SnapshotState.WAITING_EVENT) {
            throw new IllegalArgumentException("save expects a SUSPENDED or WAITING_EVENT snapshot");
        }
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
            throw new ActionGraphIntegrationException("Cannot save suspended run: " + run.runId(), ex);
        }
    }

    @Override
    public void saveCheckpoint(SuspendedRun checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        if (checkpoint.snapshotState() != SnapshotState.RUNNING) {
            throw new IllegalArgumentException("checkpoint must have RUNNING snapshotState");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                delete(connection, checkpoint.runId());
                insert(connection, checkpoint);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot save running checkpoint: " + checkpoint.runId(), ex);
        }
    }

    @Override
    public Optional<SuspendedRun> findByRunId(String runId) {
        Objects.requireNonNull(runId, "runId");
        try (Connection connection = dataSource.getConnection()) {
            return findByRunId(connection, runId);
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot find suspended run: " + runId, ex);
        }
    }

    @Override
    public Optional<SuspendedRun> claimForResume(String runId) {
        Objects.requireNonNull(runId, "runId");
        Instant now = Instant.now();
        Instant staleBefore = now.minus(claimTimeout);
        String sql = "update " + table + " set status = ?, claimed_at = ?, updated_at = ? "
                + "where run_id = ? and snapshot_state = ? "
                + "and (status is null or status = ? or (status = ? and claimed_at < ?))";
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, STATUS_RESUMING);
                statement.setString(2, now.toString());
                statement.setString(3, now.toString());
                statement.setString(4, runId);
                statement.setString(5, SnapshotState.SUSPENDED.name());
                statement.setString(6, STATUS_SUSPENDED);
                statement.setString(7, STATUS_RESUMING);
                statement.setString(8, staleBefore.toString());
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
            throw new ActionGraphIntegrationException("Cannot claim suspended run for resume: " + runId, ex);
        }
    }

    @Override
    public Optional<SuspendedRun> claimWaitingEvent(String eventType, String correlationId) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        Instant now = Instant.now();
        Instant claimStaleBefore = now.minus(claimTimeout);
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Optional<String> runId = firstClaimableWaitingRunId(
                        connection, eventType.trim(), correlationId.trim(), null, claimStaleBefore);
                if (runId.isEmpty()) {
                    connection.commit();
                    return Optional.empty();
                }
                if (!claimWaitingRun(connection, runId.get(), claimStaleBefore, now)) {
                    connection.commit();
                    return Optional.empty();
                }
                Optional<SuspendedRun> run = findByRunId(connection, runId.get());
                connection.commit();
                return run;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot claim waiting event: " + eventType + "/" + correlationId, ex);
        }
    }

    @Override
    public Optional<SuspendedRun> claimExpiredWaiting(Instant now) {
        Objects.requireNonNull(now, "now");
        Instant claimStaleBefore = Instant.now().minus(claimTimeout);
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Optional<String> runId = firstClaimableWaitingRunId(connection, null, null, now, claimStaleBefore);
                if (runId.isEmpty()) {
                    connection.commit();
                    return Optional.empty();
                }
                if (!claimWaitingRun(connection, runId.get(), claimStaleBefore, Instant.now())) {
                    connection.commit();
                    return Optional.empty();
                }
                Optional<SuspendedRun> run = findByRunId(connection, runId.get());
                connection.commit();
                return run;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot claim expired waiting event", ex);
        }
    }

    @Override
    public boolean markInFlight(String runId, ActionId actionId) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(actionId, "actionId");
        Instant now = Instant.now();
        String sql = "update " + table
                + " set in_flight_action_id = ?, heartbeat_at = ?, updated_at = ? "
                + "where run_id = ? and snapshot_state = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, actionId.value());
            statement.setString(2, now.toString());
            statement.setString(3, now.toString());
            statement.setString(4, runId);
            statement.setString(5, SnapshotState.RUNNING.name());
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot mark in-flight action: " + runId, ex);
        }
    }

    @Override
    public void heartbeat(String runId) {
        Objects.requireNonNull(runId, "runId");
        Instant now = Instant.now();
        String sql = "update " + table + " set heartbeat_at = ?, updated_at = ? "
                + "where run_id = ? and snapshot_state = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, now.toString());
            statement.setString(2, now.toString());
            statement.setString(3, runId);
            statement.setString(4, SnapshotState.RUNNING.name());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot heartbeat running checkpoint: " + runId, ex);
        }
    }

    @Override
    public Optional<SuspendedRun> claimStaleRunning(Instant staleBefore) {
        Objects.requireNonNull(staleBefore, "staleBefore");
        Instant now = Instant.now();
        Instant claimStaleBefore = now.minus(claimTimeout);
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Optional<String> runId = firstClaimableRunningRunId(connection, staleBefore, claimStaleBefore);
                if (runId.isEmpty()) {
                    connection.commit();
                    return Optional.empty();
                }
                if (!claimRunning(connection, runId.get(), staleBefore, claimStaleBefore, now)) {
                    connection.commit();
                    return Optional.empty();
                }
                Optional<SuspendedRun> run = findByRunId(connection, runId.get());
                connection.commit();
                return run;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot claim stale running checkpoint", ex);
        }
    }

    private Optional<SuspendedRun> findByRunId(Connection connection, String runId) throws SQLException {
        String sql = "select run_id, snapshot_version, goal_json, blackboard_json, executed_actions_json, "
                + "compensation_stack_json, pending_action_id, message, snapshot_state, heartbeat_at, "
                + "in_flight_action_id, event_type, event_correlation_id, event_deadline "
                + "from " + table + " where run_id = ?";
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
                        readActionId(resultSet.getString("pending_action_id")),
                        resultSet.getString("message"),
                        readSnapshotState(resultSet.getString("snapshot_state")),
                        readInstantOrNow(resultSet.getString("heartbeat_at")),
                        readActionId(resultSet.getString("in_flight_action_id")),
                        readNullableString(resultSet.getString("event_type")),
                        readNullableString(resultSet.getString("event_correlation_id")),
                        readInstantOrNull(resultSet.getString("event_deadline"))
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
            throw new ActionGraphIntegrationException("Cannot delete suspended run: " + runId, ex);
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
                + "pending_action_id, message, snapshot_state, heartbeat_at, in_flight_action_id, event_type, "
                + "event_correlation_id, event_deadline, status, claimed_at, updated_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, run.runId());
            statement.setInt(2, SNAPSHOT_FORMAT_VERSION);
            statement.setString(3, codec.writeGoal(run.goal()));
            statement.setString(4, codec.writeBlackboard(run.blackboard()));
            statement.setString(5, codec.writeActionIds(run.executedActions()));
            statement.setString(6, codec.writeActionIds(run.compensationStack()));
            statement.setString(7, run.pendingActionId() == null ? "" : run.pendingActionId().value());
            statement.setString(8, run.message());
            statement.setString(9, run.snapshotState().name());
            statement.setString(10, run.heartbeatAt().toString());
            if (run.inFlightActionId() == null) {
                statement.setNull(11, Types.VARCHAR);
            } else {
                statement.setString(11, run.inFlightActionId().value());
            }
            if (run.eventType() == null) {
                statement.setNull(12, Types.VARCHAR);
            } else {
                statement.setString(12, run.eventType());
            }
            if (run.eventCorrelationId() == null) {
                statement.setNull(13, Types.VARCHAR);
            } else {
                statement.setString(13, run.eventCorrelationId());
            }
            if (run.eventDeadline() == null) {
                statement.setNull(14, Types.VARCHAR);
            } else {
                statement.setString(14, run.eventDeadline().toString());
            }
            statement.setString(15, STATUS_SUSPENDED);
            statement.setNull(16, Types.VARCHAR);
            statement.setString(17, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private Optional<String> firstClaimableWaitingRunId(
            Connection connection,
            String eventType,
            String correlationId,
            Instant deadlineAtOrBefore,
            Instant claimStaleBefore
    ) throws SQLException {
        StringBuilder sql = new StringBuilder("select run_id from ").append(table)
                .append(" where snapshot_state = ? ")
                .append("and (status is null or status = ? or (status = ? and claimed_at < ?)) ");
        if (eventType != null) {
            sql.append("and event_type = ? and event_correlation_id = ? ");
        }
        if (deadlineAtOrBefore != null) {
            sql.append("and event_deadline <= ? ");
        }
        sql.append("order by updated_at");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setString(index++, SnapshotState.WAITING_EVENT.name());
            statement.setString(index++, STATUS_SUSPENDED);
            statement.setString(index++, STATUS_RESUMING);
            statement.setString(index++, claimStaleBefore.toString());
            if (eventType != null) {
                statement.setString(index++, eventType);
                statement.setString(index++, correlationId);
            }
            if (deadlineAtOrBefore != null) {
                statement.setString(index, deadlineAtOrBefore.toString());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getString("run_id"));
                }
                return Optional.empty();
            }
        }
    }

    private boolean claimWaitingRun(
            Connection connection,
            String runId,
            Instant claimStaleBefore,
            Instant now
    ) throws SQLException {
        String sql = "update " + table + " set status = ?, claimed_at = ?, updated_at = ? "
                + "where run_id = ? and snapshot_state = ? "
                + "and (status is null or status = ? or (status = ? and claimed_at < ?))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, STATUS_RESUMING);
            statement.setString(2, now.toString());
            statement.setString(3, now.toString());
            statement.setString(4, runId);
            statement.setString(5, SnapshotState.WAITING_EVENT.name());
            statement.setString(6, STATUS_SUSPENDED);
            statement.setString(7, STATUS_RESUMING);
            statement.setString(8, claimStaleBefore.toString());
            return statement.executeUpdate() == 1;
        }
    }

    private Optional<String> firstClaimableRunningRunId(
            Connection connection,
            Instant staleBefore,
            Instant claimStaleBefore
    ) throws SQLException {
        String sql = "select run_id from " + table
                + " where snapshot_state = ? and heartbeat_at < ? "
                + "and (status is null or status = ? or (status = ? and claimed_at < ?)) "
                + "order by updated_at";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SnapshotState.RUNNING.name());
            statement.setString(2, staleBefore.toString());
            statement.setString(3, STATUS_SUSPENDED);
            statement.setString(4, STATUS_RESUMING);
            statement.setString(5, claimStaleBefore.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getString("run_id"));
                }
                return Optional.empty();
            }
        }
    }

    private boolean claimRunning(
            Connection connection,
            String runId,
            Instant staleBefore,
            Instant claimStaleBefore,
            Instant now
    ) throws SQLException {
        String sql = "update " + table + " set status = ?, claimed_at = ?, updated_at = ? "
                + "where run_id = ? and snapshot_state = ? and heartbeat_at < ? "
                + "and (status is null or status = ? or (status = ? and claimed_at < ?))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, STATUS_RESUMING);
            statement.setString(2, now.toString());
            statement.setString(3, now.toString());
            statement.setString(4, runId);
            statement.setString(5, SnapshotState.RUNNING.name());
            statement.setString(6, staleBefore.toString());
            statement.setString(7, STATUS_SUSPENDED);
            statement.setString(8, STATUS_RESUMING);
            statement.setString(9, claimStaleBefore.toString());
            return statement.executeUpdate() == 1;
        }
    }

    private ActionId readActionId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new ActionId(value);
    }

    private SnapshotState readSnapshotState(String value) {
        if (value == null || value.isBlank()) {
            return SnapshotState.SUSPENDED;
        }
        return SnapshotState.valueOf(value);
    }

    private String readNullableString(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Instant readInstantOrNow(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(value);
    }

    private Instant readInstantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
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
