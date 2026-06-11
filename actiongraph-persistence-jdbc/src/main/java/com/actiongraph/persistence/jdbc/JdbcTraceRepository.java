package com.actiongraph.persistence.jdbc;

import com.actiongraph.exception.ActionGraphIntegrationException;
import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class JdbcTraceRepository implements TraceRepository {
    public static final String DEFAULT_TABLE = "actiongraph_trace_event";

    private final DataSource dataSource;
    private final PersistenceJsonCodec codec;
    private final String table;

    public JdbcTraceRepository(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE);
    }

    public JdbcTraceRepository(DataSource dataSource, String table) {
        this(dataSource, table, new PersistenceJsonCodec());
    }

    JdbcTraceRepository(DataSource dataSource, String table, PersistenceJsonCodec codec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.table = validateIdentifier(table);
        this.codec = Objects.requireNonNull(codec, "codec");
        initializeSchema();
    }

    public void initializeSchema() {
        String sql = "create table if not exists " + table + " ("
                + "run_id varchar(128) not null,"
                + "seq bigint not null,"
                + "at varchar(64) not null,"
                + "type varchar(64) not null,"
                + "action_id varchar(256),"
                + "detail clob not null,"
                + "data_json clob not null,"
                + "prev_hash varchar(64),"
                + "hash varchar(64),"
                + "primary key (run_id, seq)"
                + ")";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            ensureColumn(connection, "prev_hash", "varchar(64)");
            ensureColumn(connection, "hash", "varchar(64)");
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot initialize trace repository schema", ex);
        }
    }

    @Override
    public void append(TraceEvent event) {
        Objects.requireNonNull(event, "event");
        appendAll(List.of(event));
    }

    @Override
    public void appendAll(Collection<TraceEvent> events) {
        Objects.requireNonNull(events, "events");
        if (events.isEmpty()) {
            return;
        }
        String sql = "insert into " + table
                + " (run_id, seq, at, type, action_id, detail, data_json, prev_hash, hash) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (TraceEvent event : events) {
                statement.setString(1, event.runId());
                statement.setLong(2, event.seq());
                statement.setString(3, event.at().toString());
                statement.setString(4, event.type().name());
                statement.setString(5, event.actionId());
                statement.setString(6, event.detail());
                statement.setString(7, codec.writeTraceData(event.data()));
                statement.setString(8, event.prevHash());
                statement.setString(9, event.hash());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot append trace events", ex);
        }
    }

    @Override
    public List<TraceEvent> findByRun(String runId) {
        Objects.requireNonNull(runId, "runId");
        String sql = "select run_id, seq, at, type, action_id, detail, data_json, prev_hash, hash from " + table
                + " where run_id = ? order by seq";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TraceEvent> events = new ArrayList<>();
                while (resultSet.next()) {
                    events.add(new TraceEvent(
                            resultSet.getString("run_id"),
                            resultSet.getLong("seq"),
                            Instant.parse(resultSet.getString("at")),
                            TraceEventType.valueOf(resultSet.getString("type")),
                            resultSet.getString("action_id"),
                            resultSet.getString("detail"),
                            codec.readTraceData(resultSet.getString("data_json")),
                            resultSet.getString("prev_hash"),
                            resultSet.getString("hash")
                    ));
                }
                return List.copyOf(events);
            }
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot find trace events for runId: " + runId, ex);
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

    public static String validateIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("SQL identifier must contain only letters, numbers, and underscores");
        }
        return identifier;
    }
}
