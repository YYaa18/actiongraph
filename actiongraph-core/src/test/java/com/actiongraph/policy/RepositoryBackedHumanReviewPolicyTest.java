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
            assertThat(task.stages()).containsExactly(new ApprovalStage("review", "reviewer"));
            assertThat(task.currentStageIndex()).isZero();
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
    void riskBasedChainRequiresAllStagesBeforeApproval() {
        InMemoryHumanReviewRepository repository = new InMemoryHumanReviewRepository();
        RepositoryBackedHumanReviewPolicy policy = new RepositoryBackedHumanReviewPolicy(
                repository,
                multiStageResolver()
        );
        policy.review(request("RUN-1"));
        HumanReviewTask task = repository.findPending().getFirst();

        assertThat(task.stages())
                .extracting(ApprovalStage::name)
                .containsExactly("checker-review", "authorization");

        repository.decideStage("RUN-1", ACTION_ID, 0,
                HumanReviewDecision.APPROVED, "checker-1", "checker approved");

        HumanReviewResult afterFirstStage = policy.review(request("RUN-1"));
        assertThat(afterFirstStage.decision()).isEqualTo(HumanReviewDecision.PENDING);
        assertThat(repository.findPending()).singleElement().satisfies(pending -> {
            assertThat(pending.currentStageIndex()).isEqualTo(1);
            assertThat(pending.stageDecisions()).singleElement()
                    .satisfies(decision -> {
                        assertThat(decision.stage()).isEqualTo("checker-review");
                        assertThat(decision.reviewer()).isEqualTo("checker-1");
                    });
        });

        repository.decideStage("RUN-1", ACTION_ID, 1,
                HumanReviewDecision.APPROVED, "authorizer-1", "authorization approved");

        HumanReviewResult result = policy.review(request("RUN-1"));
        assertThat(result.decision()).isEqualTo(HumanReviewDecision.APPROVED);
        assertThat(result.reviewer()).isEqualTo("authorizer-1");
        assertThat(result.message())
                .contains("checker-review=APPROVED by checker-1")
                .contains("authorization=APPROVED by authorizer-1");
    }

    @Test
    void riskBasedChainDenialStopsChain() {
        InMemoryHumanReviewRepository repository = new InMemoryHumanReviewRepository();
        RepositoryBackedHumanReviewPolicy policy = new RepositoryBackedHumanReviewPolicy(
                repository,
                multiStageResolver()
        );
        policy.review(request("RUN-1"));

        repository.decideStage("RUN-1", ACTION_ID, 0,
                HumanReviewDecision.DENIED, "checker-1", "blocked");

        HumanReviewResult result = policy.review(request("RUN-1"));
        assertThat(result.decision()).isEqualTo(HumanReviewDecision.DENIED);
        assertThat(result.reviewer()).isEqualTo("checker-1");
        assertThat(repository.find("RUN-1", ACTION_ID)).get().satisfies(task -> {
            assertThat(task.currentStageIndex()).isZero();
            assertThat(task.stageDecisions()).singleElement()
                    .satisfies(decision -> assertThat(decision.decision()).isEqualTo(HumanReviewDecision.DENIED));
        });
    }

    @Test
    void rejectsRepeatedDecisionForSameStage() {
        InMemoryHumanReviewRepository repository = new InMemoryHumanReviewRepository();
        RepositoryBackedHumanReviewPolicy policy = new RepositoryBackedHumanReviewPolicy(
                repository,
                multiStageResolver()
        );
        policy.review(request("RUN-1"));

        repository.decideStage("RUN-1", ACTION_ID, 0,
                HumanReviewDecision.APPROVED, "checker-1", "approved");

        assertThatThrownBy(() -> repository.decideStage(
                "RUN-1",
                ACTION_ID,
                0,
                HumanReviewDecision.APPROVED,
                "checker-2",
                "duplicate"
        )).isInstanceOf(StageAlreadyDecidedException.class);
    }

    @Test
    void customChainResolverCanUseRequestAttributes() {
        InMemoryHumanReviewRepository repository = new InMemoryHumanReviewRepository();
        RepositoryBackedHumanReviewPolicy policy = new RepositoryBackedHumanReviewPolicy(
                repository,
                request -> {
                    if ("true".equalsIgnoreCase(request.attributes().get("amountEscalated"))) {
                        return new ApprovalChain(List.of(
                                new ApprovalStage("review", "reviewer"),
                                new ApprovalStage("amount-authorization", "authorizer")
                        ));
                    }
                    return ApprovalChain.single();
                }
        );

        policy.review(new HumanReviewRequest(
                "RUN-1",
                ACTION_ID,
                ActionRiskLevel.MEDIUM,
                true,
                new Plan(List.of(new PlanStep(ACTION_ID))),
                Set.of(Condition.of("risk:READY")),
                Map.of("customerId", "C001"),
                Map.of("amountEscalated", "true")
        ));

        assertThat(repository.findPending()).singleElement()
                .satisfies(task -> assertThat(task.stages())
                        .extracting(ApprovalStage::name)
                        .containsExactly("review", "amount-authorization"));
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

    private ApprovalChainResolver multiStageResolver() {
        return request -> new ApprovalChain(List.of(
                new ApprovalStage("checker-review", "checker"),
                new ApprovalStage("authorization", "authorizer")
        ));
    }
}
