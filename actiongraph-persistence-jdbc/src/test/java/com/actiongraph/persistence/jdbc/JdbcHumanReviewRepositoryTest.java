package com.actiongraph.persistence.jdbc;

import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Plan;
import com.actiongraph.planning.PlanStep;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewRequest;
import com.actiongraph.policy.HumanReviewTask;
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
        return new HumanReviewRequest(
                runId,
                ACTION_ID,
                ActionRiskLevel.HIGH,
                true,
                new Plan(List.of(new PlanStep(ACTION_ID))),
                Set.of(Condition.of("risk:READY")),
                Map.of("customerId", "C001")
        );
    }
}
