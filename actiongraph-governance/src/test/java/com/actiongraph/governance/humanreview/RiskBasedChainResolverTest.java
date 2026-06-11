package com.actiongraph.governance.humanreview;

import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Plan;
import com.actiongraph.planning.PlanStep;
import com.actiongraph.policy.HumanReviewRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RiskBasedChainResolverTest {
    private static final ActionId ACTION_ID = new ActionId("risk.action");

    @Test
    void highRiskActionsRequireCheckerAndAuthorizerStages() {
        RiskBasedChainResolver resolver = new RiskBasedChainResolver();

        assertThat(resolver.resolve(request(ActionRiskLevel.HIGH, Map.of())).stages())
                .extracting(stage -> stage.name())
                .containsExactly("checker-review", "authorization");
    }

    @Test
    void nonHighRiskActionsUseSingleReviewStage() {
        RiskBasedChainResolver resolver = new RiskBasedChainResolver();

        assertThat(resolver.resolve(request(ActionRiskLevel.MEDIUM, Map.of())).stages())
                .extracting(stage -> stage.name())
                .containsExactly("review");
    }

    @Test
    void amountEscalationAddsAmountAuthorizationStage() {
        RiskBasedChainResolver resolver = new RiskBasedChainResolver();

        assertThat(resolver.resolve(request(ActionRiskLevel.HIGH, Map.of("amountEscalated", "true"))).stages())
                .extracting(stage -> stage.name())
                .containsExactly("checker-review", "authorization", "amount-authorization");
    }

    private HumanReviewRequest request(ActionRiskLevel riskLevel, Map<String, String> attributes) {
        return new HumanReviewRequest(
                "RUN-1",
                ACTION_ID,
                riskLevel,
                true,
                new Plan(List.of(new PlanStep(ACTION_ID))),
                Set.of(Condition.of("risk:READY")),
                Map.of("customerId", "C001"),
                attributes
        );
    }
}
