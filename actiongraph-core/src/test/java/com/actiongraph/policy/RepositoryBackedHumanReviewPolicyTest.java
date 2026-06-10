package com.actiongraph.policy;

import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Plan;
import com.actiongraph.planning.PlanStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryBackedHumanReviewPolicyTest {
    private static final ActionId ACTION_ID = new ActionId("risk.action");

    @Test
    void firstReviewCreatesPendingTask() {
        InMemoryHumanReviewRepository repository = new InMemoryHumanReviewRepository();
        RepositoryBackedHumanReviewPolicy policy = new RepositoryBackedHumanReviewPolicy(repository);

        HumanReviewResult result = policy.review(request("RUN-1"));

        assertThat(result.decision()).isEqualTo(HumanReviewDecision.PENDING);
        assertThat(repository.findPending()).singleElement().satisfies(task -> {
            assertThat(task.runId()).isEqualTo("RUN-1");
            assertThat(task.actionId()).isEqualTo(ACTION_ID);
            assertThat(task.planPreview()).containsExactly(ACTION_ID);
            assertThat(task.currentState()).containsExactly(Condition.of("risk:READY"));
            assertThat(task.blackboardPreview()).containsEntry("customerId", "C001");
        });
    }

    @Test
    void recordedApprovalIsReturnedOnNextReview() {
        InMemoryHumanReviewRepository repository = new InMemoryHumanReviewRepository();
        RepositoryBackedHumanReviewPolicy policy = new RepositoryBackedHumanReviewPolicy(repository);
        policy.review(request("RUN-1"));

        repository.decide("RUN-1", ACTION_ID, HumanReviewDecision.APPROVED, "approver-1", "approved");

        HumanReviewResult result = policy.review(request("RUN-1"));

        assertThat(result.decision()).isEqualTo(HumanReviewDecision.APPROVED);
        assertThat(result.reviewer()).isEqualTo("approver-1");
        assertThat(result.message()).isEqualTo("approved");
        assertThat(repository.findPending()).isEmpty();
    }

    @Test
    void recordedDenialIsReturnedOnNextReview() {
        InMemoryHumanReviewRepository repository = new InMemoryHumanReviewRepository();
        RepositoryBackedHumanReviewPolicy policy = new RepositoryBackedHumanReviewPolicy(repository);
        policy.review(request("RUN-1"));

        repository.decide("RUN-1", ACTION_ID, HumanReviewDecision.DENIED, "approver-1", "denied");

        HumanReviewResult result = policy.review(request("RUN-1"));

        assertThat(result.decision()).isEqualTo(HumanReviewDecision.DENIED);
        assertThat(result.reviewer()).isEqualTo("approver-1");
        assertThat(result.message()).isEqualTo("denied");
    }

    @Test
    void cannotDecideMissingOrPendingTask() {
        InMemoryHumanReviewRepository repository = new InMemoryHumanReviewRepository();

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
