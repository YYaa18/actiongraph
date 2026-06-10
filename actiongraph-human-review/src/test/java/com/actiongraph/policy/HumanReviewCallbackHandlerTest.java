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

class HumanReviewCallbackHandlerTest {
    private static final ActionId ACTION_ID = new ActionId("claim.approval.request");

    @Test
    void callbackApprovesPendingReviewTask() {
        InMemoryHumanReviewRepository repository = new InMemoryHumanReviewRepository();
        new RepositoryBackedHumanReviewPolicy(repository).review(request());
        HumanReviewCallbackHandler handler = new HumanReviewCallbackHandler(repository);

        HumanReviewTask task = handler.handle(new HumanReviewCallback(
                "RUN-1",
                ACTION_ID,
                0,
                HumanReviewDecision.APPROVED,
                "claims-checker",
                "approved"
        ));

        assertThat(task.decision()).isEqualTo(HumanReviewDecision.APPROVED);
        assertThat(task.reviewer()).isEqualTo("claims-checker");
        assertThat(task.message()).isEqualTo("approved");
        assertThat(task.stageDecisions()).singleElement().satisfies(decision -> {
            assertThat(decision.stage()).isEqualTo("review");
            assertThat(decision.decision()).isEqualTo(HumanReviewDecision.APPROVED);
            assertThat(decision.reviewer()).isEqualTo("claims-checker");
        });
    }

    @Test
    void callbackCanDenyPendingReviewTask() {
        InMemoryHumanReviewRepository repository = new InMemoryHumanReviewRepository();
        new RepositoryBackedHumanReviewPolicy(repository).review(request());
        HumanReviewCallbackHandler handler = new HumanReviewCallbackHandler(repository);

        HumanReviewTask task = handler.handle(new HumanReviewCallback(
                "RUN-1",
                ACTION_ID,
                0,
                HumanReviewDecision.DENIED,
                "risk-officer",
                "blocked"
        ));

        assertThat(task.decision()).isEqualTo(HumanReviewDecision.DENIED);
        assertThat(task.reviewer()).isEqualTo("risk-officer");
        assertThat(task.stageDecisions()).singleElement()
                .satisfies(decision -> assertThat(decision.decision()).isEqualTo(HumanReviewDecision.DENIED));
    }

    @Test
    void callbackRejectsPendingDecisionAndAlreadyDecidedStage() {
        InMemoryHumanReviewRepository repository = new InMemoryHumanReviewRepository();
        new RepositoryBackedHumanReviewPolicy(repository).review(request());
        HumanReviewCallbackHandler handler = new HumanReviewCallbackHandler(repository);

        assertThatThrownBy(() -> new HumanReviewCallback(
                "RUN-1",
                ACTION_ID,
                0,
                HumanReviewDecision.PENDING,
                "checker",
                "later"
        )).isInstanceOf(IllegalArgumentException.class);

        handler.handle(new HumanReviewCallback(
                "RUN-1",
                ACTION_ID,
                0,
                HumanReviewDecision.APPROVED,
                "checker-1",
                "approved"
        ));

        assertThatThrownBy(() -> handler.handle(new HumanReviewCallback(
                "RUN-1",
                ACTION_ID,
                0,
                HumanReviewDecision.APPROVED,
                "checker-2",
                "duplicate"
        ))).isInstanceOf(StageAlreadyDecidedException.class);
    }

    @Test
    void callbackRejectsMissingTask() {
        HumanReviewCallbackHandler handler = new HumanReviewCallbackHandler(new InMemoryHumanReviewRepository());

        assertThatThrownBy(() -> handler.handle(new HumanReviewCallback(
                "RUN-404",
                ACTION_ID,
                0,
                HumanReviewDecision.APPROVED,
                "checker",
                "approved"
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No human review task found");
    }

    private HumanReviewRequest request() {
        return new HumanReviewRequest(
                "RUN-1",
                ACTION_ID,
                ActionRiskLevel.HIGH,
                true,
                new Plan(List.of(new PlanStep(ACTION_ID))),
                Set.of(Condition.of("claims:READY")),
                Map.of("claimId", "CLM100")
        );
    }
}
