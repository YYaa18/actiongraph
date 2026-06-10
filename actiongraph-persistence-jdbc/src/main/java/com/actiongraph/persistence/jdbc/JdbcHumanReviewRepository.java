package com.actiongraph.persistence.jdbc;

import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewRepository;
import com.actiongraph.policy.HumanReviewTask;

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

public final class JdbcHumanReviewRepository implements HumanReviewRepository {
    public static final String DEFAULT_TABLE = "actiongraph_human_review_task";

    private final DataSource dataSource;
    private final PersistenceJsonCodec codec;
    private final String table;

    public JdbcHumanReviewRepository(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE);
    }

    public JdbcHumanReviewRepository(DataSource dataSource, String table) {
        this(dataSource, table, new PersistenceJsonCodec());
    }

    JdbcHumanReviewRepository(DataSource dataSource, String table, PersistenceJsonCodec codec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.table = JdbcTraceRepository.validateIdentifier(table);
        this.codec = Objects.requireNonNull(codec, "codec");
        initializeSchema();
    }

    public void initializeSchema() {
        String sql = "create table if not exists " + table + " ("
                + "run_id varchar(128) not null,"
                + "action_id varchar(256) not null,"
                + "risk_level varchar(64) not null,"
                + "required_by_action boolean not null,"
                + "plan_preview_json clob not null,"
                + "current_state_json clob not null,"
                + "blackboard_preview_json clob not null,"
                + "decision varchar(64) not null,"
                + "reviewer varchar(256) not null,"
                + "message clob not null,"
                + "created_at varchar(64) not null,"
                + "updated_at varchar(64) not null,"
                + "primary key (run_id, action_id)"
                + ")";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot initialize human review repository schema", ex);
        }
    }

    @Override
    public void savePending(HumanReviewTask task) {
        Objects.requireNonNull(task, "task");
        if (task.decision() != HumanReviewDecision.PENDING) {
            throw new IllegalArgumentException("Only pending human review tasks can be saved as pending");
        }
        if (find(task.runId(), task.actionId()).isPresent()) {
            return;
        }
        String sql = "insert into " + table
                + " (run_id, action_id, risk_level, required_by_action, plan_preview_json, current_state_json, "
                + "blackboard_preview_json, decision, reviewer, message, created_at, updated_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindTask(statement, task);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Cannot save human review task: " + task.runId() + "/" + task.actionId().value(), ex);
        }
    }

    @Override
    public Optional<HumanReviewTask> find(String runId, ActionId actionId) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(actionId, "actionId");
        String sql = selectSql() + " where run_id = ? and action_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            statement.setString(2, actionId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(readTask(resultSet))
                        : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot find human review task: " + runId + "/" + actionId.value(), ex);
        }
    }

    @Override
    public List<HumanReviewTask> findByRun(String runId) {
        Objects.requireNonNull(runId, "runId");
        String sql = selectSql() + " where run_id = ? order by action_id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            return readTasks(statement);
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot find human review tasks for runId: " + runId, ex);
        }
    }

    @Override
    public List<HumanReviewTask> findPending() {
        String sql = selectSql() + " where decision = ? order by created_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, HumanReviewDecision.PENDING.name());
            return readTasks(statement);
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot find pending human review tasks", ex);
        }
    }

    @Override
    public void decide(
            String runId,
            ActionId actionId,
            HumanReviewDecision decision,
            String reviewer,
            String message
    ) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(decision, "decision");
        if (decision == HumanReviewDecision.PENDING) {
            throw new IllegalArgumentException("Human review task decision must be APPROVED or DENIED");
        }
        HumanReviewTask existing = find(runId, actionId)
                .orElseThrow(() -> new IllegalStateException(
                        "No human review task found for " + runId + "/" + actionId.value()));
        HumanReviewTask decided = existing.withDecision(decision, reviewer, message, Instant.now());
        String sql = "update " + table
                + " set decision = ?, reviewer = ?, message = ?, updated_at = ? where run_id = ? and action_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, decided.decision().name());
            statement.setString(2, decided.reviewer());
            statement.setString(3, decided.message());
            statement.setString(4, decided.updatedAt().toString());
            statement.setString(5, runId);
            statement.setString(6, actionId.value());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot decide human review task: " + runId + "/" + actionId.value(), ex);
        }
    }

    private String selectSql() {
        return "select run_id, action_id, risk_level, required_by_action, plan_preview_json, current_state_json, "
                + "blackboard_preview_json, decision, reviewer, message, created_at, updated_at from " + table;
    }

    private List<HumanReviewTask> readTasks(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            List<HumanReviewTask> tasks = new ArrayList<>();
            while (resultSet.next()) {
                tasks.add(readTask(resultSet));
            }
            return List.copyOf(tasks);
        }
    }

    private HumanReviewTask readTask(ResultSet resultSet) throws SQLException {
        return new HumanReviewTask(
                resultSet.getString("run_id"),
                new ActionId(resultSet.getString("action_id")),
                ActionRiskLevel.valueOf(resultSet.getString("risk_level")),
                resultSet.getBoolean("required_by_action"),
                codec.readActionIds(resultSet.getString("plan_preview_json")),
                codec.readConditions(resultSet.getString("current_state_json")),
                codec.readTraceData(resultSet.getString("blackboard_preview_json")),
                HumanReviewDecision.valueOf(resultSet.getString("decision")),
                resultSet.getString("reviewer"),
                resultSet.getString("message"),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private void bindTask(PreparedStatement statement, HumanReviewTask task) throws SQLException {
        statement.setString(1, task.runId());
        statement.setString(2, task.actionId().value());
        statement.setString(3, task.riskLevel().name());
        statement.setBoolean(4, task.requiredByAction());
        statement.setString(5, codec.writeActionIds(task.planPreview()));
        statement.setString(6, codec.writeConditions(task.currentState()));
        statement.setString(7, codec.writeTraceData(task.blackboardPreview()));
        statement.setString(8, task.decision().name());
        statement.setString(9, task.reviewer());
        statement.setString(10, task.message());
        statement.setString(11, task.createdAt().toString());
        statement.setString(12, task.updatedAt().toString());
    }
}
