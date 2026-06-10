package com.actiongraph.humanreview.api;

import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Plan;
import com.actiongraph.planning.PlanStep;
import com.actiongraph.policy.ApprovalChain;
import com.actiongraph.policy.ApprovalStage;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewRequest;
import com.actiongraph.policy.HumanReviewRepository;
import com.actiongraph.policy.HumanReviewTask;
import com.actiongraph.policy.InMemoryHumanReviewRepository;
import com.actiongraph.policy.StageAlreadyDecidedException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HumanReviewApiServiceTest {
    private static final ActionId ACTION_ID = new ActionId("claim.approval.request");

    @Test
    void mapsPendingTasksToStableResponses() {
        HumanReviewRepository repository = new InMemoryHumanReviewRepository();
        repository.savePending(HumanReviewTask.pending(request("RUN-1"), "Review required",
                new ApprovalChain(List.of(
                        new ApprovalStage("checker", "claims-checker"),
                        new ApprovalStage("authorizer", "risk-officer")
                ))));

        HumanReviewTaskResponse response = new HumanReviewApiService(repository).pendingTasks().getFirst();

        assertThat(response.runId()).isEqualTo("RUN-1");
        assertThat(response.actionId()).isEqualTo(ACTION_ID.value());
        assertThat(response.riskLevel()).isEqualTo(ActionRiskLevel.HIGH);
        assertThat(response.planPreview()).containsExactly("claim.approval.request", "claim.payout.submit");
        assertThat(response.currentState()).containsExactly("claims:DRAFT_READY", "claims:READY");
        assertThat(response.blackboardPreview()).containsEntry("claimId", "CLM100");
        assertThat(response.attributes()).containsEntry("amount", "120000");
        assertThat(response.decision()).isEqualTo(HumanReviewDecision.PENDING);
        assertThat(response.currentStageIndex()).isZero();
        assertThat(response.currentStageName()).isEqualTo("checker");
        assertThat(response.stages()).extracting(HumanReviewStageResponse::name)
                .containsExactly("checker", "authorizer");
    }

    @Test
    void decidesExpectedStageAndReturnsUpdatedTask() {
        HumanReviewRepository repository = new InMemoryHumanReviewRepository();
        repository.savePending(HumanReviewTask.pending(request("RUN-1"), "Review required",
                new ApprovalChain(List.of(
                        new ApprovalStage("checker", "claims-checker"),
                        new ApprovalStage("authorizer", "risk-officer")
                ))));
        HumanReviewApiService service = new HumanReviewApiService(repository);

        HumanReviewTaskResponse first = service.decide(
                "RUN-1",
                ACTION_ID.value(),
                0,
                HumanReviewDecision.APPROVED,
                "checker-1",
                "checker approved"
        );
        HumanReviewTaskResponse second = service.decide(
                "RUN-1",
                ACTION_ID.value(),
                1,
                HumanReviewDecision.APPROVED,
                "risk-1",
                "risk approved"
        );

        assertThat(first.decision()).isEqualTo(HumanReviewDecision.PENDING);
        assertThat(first.currentStageIndex()).isEqualTo(1);
        assertThat(first.currentStageName()).isEqualTo("authorizer");
        assertThat(first.stageDecisions()).singleElement()
                .satisfies(decision -> {
                    assertThat(decision.stage()).isEqualTo("checker");
                    assertThat(decision.reviewer()).isEqualTo("checker-1");
                });
        assertThat(second.decision()).isEqualTo(HumanReviewDecision.APPROVED);
        assertThat(second.currentStageIndex()).isEqualTo(2);
        assertThat(second.currentStageName()).isEmpty();
        assertThat(second.stageDecisions()).hasSize(2);
    }

    @Test
    void rejectsMissingTasksAndRepeatedStageDecisions() {
        HumanReviewRepository repository = new InMemoryHumanReviewRepository();
        repository.savePending(HumanReviewTask.pending(request("RUN-1"), "Review required"));
        HumanReviewApiService service = new HumanReviewApiService(repository);

        assertThatThrownBy(() -> service.task("MISSING", ACTION_ID.value()))
                .isInstanceOf(HumanReviewTaskNotFoundException.class);

        service.decide("RUN-1", ACTION_ID.value(), 0,
                HumanReviewDecision.APPROVED, "checker-1", "approved");

        assertThatThrownBy(() -> service.decide("RUN-1", ACTION_ID.value(), 0,
                HumanReviewDecision.APPROVED, "checker-1", "approved again"))
                .isInstanceOf(StageAlreadyDecidedException.class);
        assertThatThrownBy(() -> service.decide("RUN-1", ACTION_ID.value(), null,
                HumanReviewDecision.PENDING, "checker-1", "not final"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private HumanReviewRequest request(String runId) {
        return new HumanReviewRequest(
                runId,
                ACTION_ID,
                ActionRiskLevel.HIGH,
                true,
                new Plan(List.of(
                        new PlanStep(ACTION_ID),
                        new PlanStep(new ActionId("claim.payout.submit"))
                )),
                Set.of(Condition.of("claims:READY"), Condition.of("claims:DRAFT_READY")),
                Map.of("claimId", "CLM100"),
                Map.of("amount", "120000")
        );
    }
}
