package com.actiongraph.memory.jdbc;

import com.actiongraph.exception.ActionGraphIntegrationException;
import com.actiongraph.memory.MemoryRecord;
import com.actiongraph.memory.MemoryRepository;
import com.actiongraph.memory.MemoryScope;
import com.actiongraph.persistence.jdbc.JdbcTraceRepository;
import com.actiongraph.persistence.jdbc.PersistenceJsonCodec;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcMemoryRepository implements MemoryRepository {
    public static final String DEFAULT_TABLE = "actiongraph_memory_record";

    private final DataSource dataSource;
    private final PersistenceJsonCodec codec;
    private final String table;

    public JdbcMemoryRepository(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE);
    }

    public JdbcMemoryRepository(DataSource dataSource, String table) {
        this(dataSource, table, new PersistenceJsonCodec());
    }

    JdbcMemoryRepository(DataSource dataSource, String table, PersistenceJsonCodec codec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.table = JdbcTraceRepository.validateIdentifier(table);
        this.codec = Objects.requireNonNull(codec, "codec");
        initializeSchema();
    }

    public void initializeSchema() {
        String sql = "create table if not exists " + table + " ("
                + "id varchar(128) primary key,"
                + "tenant_id varchar(256) not null,"
                + "subject_id varchar(256) not null,"
                + "memory_namespace varchar(256) not null,"
                + "type varchar(256) not null,"
                + "attributes_json clob not null,"
                + "created_at varchar(64) not null,"
                + "updated_at varchar(64) not null"
                + ")";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot initialize memory repository schema", ex);
        }
    }

    @Override
    public void save(MemoryRecord record) {
        Objects.requireNonNull(record, "record");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                delete(connection, record.id());
                insert(connection, record);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot save memory record: " + record.id(), ex);
        }
    }

    @Override
    public Optional<MemoryRecord> findById(String id) {
        Objects.requireNonNull(id, "id");
        String sql = selectSql() + " where id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(readRecord(resultSet))
                        : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot find memory record: " + id, ex);
        }
    }

    @Override
    public List<MemoryRecord> findByScope(MemoryScope scope) {
        Objects.requireNonNull(scope, "scope");
        String sql = selectSql()
                + " where tenant_id = ? and subject_id = ? and memory_namespace = ? order by updated_at, id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindScope(statement, scope);
            return readRecords(statement);
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot find memory records for scope: " + scope, ex);
        }
    }

    @Override
    public List<MemoryRecord> findByScopeAndType(MemoryScope scope, String type) {
        Objects.requireNonNull(scope, "scope");
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        String sql = selectSql()
                + " where tenant_id = ? and subject_id = ? and memory_namespace = ? and type = ? "
                + "order by updated_at, id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindScope(statement, scope);
            statement.setString(4, type);
            return readRecords(statement);
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException(
                    "Cannot find memory records for scope/type: " + scope + "/" + type, ex);
        }
    }

    @Override
    public void delete(String id) {
        Objects.requireNonNull(id, "id");
        try (Connection connection = dataSource.getConnection()) {
            delete(connection, id);
        } catch (SQLException ex) {
            throw new ActionGraphIntegrationException("Cannot delete memory record: " + id, ex);
        }
    }

    private String selectSql() {
        return "select id, tenant_id, subject_id, memory_namespace, type, attributes_json, created_at, updated_at from "
                + table;
    }

    private void bindScope(PreparedStatement statement, MemoryScope scope) throws SQLException {
        statement.setString(1, scope.tenantId());
        statement.setString(2, scope.subjectId());
        statement.setString(3, scope.namespace());
    }

    private List<MemoryRecord> readRecords(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            List<MemoryRecord> records = new ArrayList<>();
            while (resultSet.next()) {
                records.add(readRecord(resultSet));
            }
            return List.copyOf(records);
        }
    }

    private MemoryRecord readRecord(ResultSet resultSet) throws SQLException {
        return new MemoryRecord(
                resultSet.getString("id"),
                new MemoryScope(
                        resultSet.getString("tenant_id"),
                        resultSet.getString("subject_id"),
                        resultSet.getString("memory_namespace")
                ),
                resultSet.getString("type"),
                codec.readTraceData(resultSet.getString("attributes_json")),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private void insert(Connection connection, MemoryRecord record) throws SQLException {
        String sql = "insert into " + table
                + " (id, tenant_id, subject_id, memory_namespace, type, attributes_json, created_at, updated_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.id());
            statement.setString(2, record.scope().tenantId());
            statement.setString(3, record.scope().subjectId());
            statement.setString(4, record.scope().namespace());
            statement.setString(5, record.type());
            statement.setString(6, codec.writeTraceData(record.attributes()));
            statement.setString(7, record.createdAt().toString());
            statement.setString(8, record.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    private void delete(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("delete from " + table + " where id = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }
}
