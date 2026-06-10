package com.actiongraph.persistence.jdbc;

import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Plan;
import com.actiongraph.planning.PlanStep;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewRequest;
import com.actiongraph.policy.HumanReviewTask;
import com.actiongraph.governance.RiskBasedChainResolver;
import com.actiongraph.policy.StageAlreadyDecidedException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcHumanReviewRepositoryTest {
    private static final ActionId ACTION_ID = new ActionId("risk.action");

    @Test
    void persistsPendingTasksAndDecisions() {
        JdbcHumanReviewRepository repository = new JdbcHumanReviewRepository(JdbcTestDataSources.h2());
        HumanReviewTask task = HumanReviewTask.pending(request("RUN-1"), "please review");

        repository.savePending(task);

        assertThat(repository.findPending()).singleElement().satisfies(restored -> {
            assertThat(restored.runId()).isEqualTo("RUN-1");
            assertThat(restored.actionId()).isEqualTo(ACTION_ID);
            assertThat(restored.riskLevel()).isEqualTo(ActionRiskLevel.HIGH);
            assertThat(restored.planPreview()).containsExactly(ACTION_ID);
            assertThat(restored.currentState()).containsExactly(Condition.of("risk:READY"));
            assertThat(restored.blackboardPreview()).containsEntry("customerId", "C001");
            assertThat(restored.attributes()).isEmpty();
            assertThat(restored.decision()).isEqualTo(HumanReviewDecision.PENDING);
        });

        repository.decide("RUN-1", ACTION_ID, HumanReviewDecision.APPROVED, "approver-1", "approved");

        assertThat(repository.findPending()).isEmpty();
        assertThat(repository.find("RUN-1", ACTION_ID)).get().satisfies(restored -> {
            assertThat(restored.decision()).isEqualTo(HumanReviewDecision.APPROVED);
            assertThat(restored.reviewer()).isEqualTo("approver-1");
            assertThat(restored.message()).isEqualTo("approved");
        });
        assertThat(repository.findByRun("RUN-1")).hasSize(1);
    }

    @Test
    void persistsMultiStageProgressionAndRejectsStaleStageDecision() {
        JdbcHumanReviewRepository repository = new JdbcHumanReviewRepository(JdbcTestDataSources.h2());
        HumanReviewRequest request = request("RUN-STAGES");
        HumanReviewTask task = HumanReviewTask.pending(
                request,
                "please review",
                new RiskBasedChainResolver().resolve(request)
        );
        repository.savePending(task);

        assertThat(repository.findPending()).singleElement().satisfies(restored -> {
            assertThat(restored.stages())
                    .extracting(stage -> stage.name())
                    .containsExactly("checker-review", "authorization");
            assertThat(restored.currentStageIndex()).isZero();
            assertThat(restored.stageDecisions()).isEmpty();
        });

        repository.decideStage("RUN-STAGES", ACTION_ID, 0,
                HumanReviewDecision.APPROVED, "checker-1", "checker approved");

        assertThat(repository.find("RUN-STAGES", ACTION_ID)).get().satisfies(restored -> {
            assertThat(restored.decision()).isEqualTo(HumanReviewDecision.PENDING);
            assertThat(restored.currentStageIndex()).isEqualTo(1);
            assertThat(restored.stageDecisions()).singleElement()
                    .satisfies(decision -> assertThat(decision.stage()).isEqualTo("checker-review"));
        });
        assertThatThrownBy(() -> repository.decideStage(
                "RUN-STAGES",
                ACTION_ID,
                0,
                HumanReviewDecision.APPROVED,
                "checker-2",
                "duplicate"
        )).isInstanceOf(StageAlreadyDecidedException.class);

        repository.decideStage("RUN-STAGES", ACTION_ID, 1,
                HumanReviewDecision.APPROVED, "authorizer-1", "authorized");

        assertThat(repository.findPending()).isEmpty();
        assertThat(repository.find("RUN-STAGES", ACTION_ID)).get().satisfies(restored -> {
            assertThat(restored.decision()).isEqualTo(HumanReviewDecision.APPROVED);
            assertThat(restored.currentStageIndex()).isEqualTo(2);
            assertThat(restored.stageDecisions()).hasSize(2);
        });
    }

    @Test
    void persistsReviewAttributes() {
        JdbcHumanReviewRepository repository = new JdbcHumanReviewRepository(JdbcTestDataSources.h2());
        HumanReviewTask task = HumanReviewTask.pending(request(
                "RUN-AMOUNT",
                Map.of(
                        "amount", "120000",
                        "currency", "CNY",
                        "amountEscalated", "true"
                )
        ), "amount review");

        repository.savePending(task);

        assertThat(repository.find("RUN-AMOUNT", ACTION_ID)).get()
                .satisfies(restored -> assertThat(restored.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "amount", "120000",
                        "currency", "CNY",
                        "amountEscalated", "true"
                )));
    }

    @Test
    void migratesLegacyReviewTableWithSingleStageDefaults() throws Exception {
        var dataSource = JdbcTestDataSources.h2();
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("create table " + JdbcHumanReviewRepository.DEFAULT_TABLE + " ("
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
                    + ")");
            statement.executeUpdate("insert into " + JdbcHumanReviewRepository.DEFAULT_TABLE
                    + " (run_id, action_id, risk_level, required_by_action, plan_preview_json, "
                    + "current_state_json, blackboard_preview_json, decision, reviewer, message, created_at, updated_at) "
                    + "values ('RUN-OLD', 'risk.action', 'HIGH', true, '[\"risk.action\"]', "
                    + "'[\"risk:READY\"]', '{\"customerId\":\"C001\"}', 'PENDING', '', 'please review', "
                    + "'2026-01-01T00:00:01Z', '2026-01-01T00:00:01Z')");
        }

        JdbcHumanReviewRepository repository = new JdbcHumanReviewRepository(dataSource);

        assertThat(repository.find("RUN-OLD", ACTION_ID)).get().satisfies(restored -> {
            assertThat(restored.stages())
                    .extracting(stage -> stage.name())
                    .containsExactly("review");
            assertThat(restored.currentStageIndex()).isZero();
            assertThat(restored.stageDecisions()).isEmpty();
            assertThat(restored.attributes()).isEmpty();
        });
    }

    @Test
    void rejectsMissingOrPendingDecision() {
        JdbcHumanReviewRepository repository = new JdbcHumanReviewRepository(JdbcTestDataSources.h2());

        assertThatThrownBy(() -> repository.decide(
                "RUN-1",
                ACTION_ID,
                HumanReviewDecision.PENDING,
                "approver-1",
                "later"
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> repository.decide(
                "RUN-1",
                ACTION_ID,
                HumanReviewDecision.APPROVED,
                "approver-1",
                "approved"
        )).isInstanceOf(IllegalStateException.class);
    }

    private HumanReviewRequest request(String runId) {
        return request(runId, Map.of());
    }

    private HumanReviewRequest request(String runId, Map<String, String> attributes) {
        return new HumanReviewRequest(
                runId,
                ACTION_ID,
                ActionRiskLevel.HIGH,
                true,
                new Plan(List.of(new PlanStep(ACTION_ID))),
                Set.of(Condition.of("risk:READY")),
                Map.of("customerId", "C001"),
                attributes
        );
    }
}
