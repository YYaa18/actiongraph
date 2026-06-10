package com.actiongraph.governance.humanreview.spring;

import com.actiongraph.governance.AmountExtractor;
import com.actiongraph.governance.MonetaryAmount;
import com.actiongraph.governance.humanreview.AmountAttributeContributor;
import com.actiongraph.governance.humanreview.RiskBasedChainResolver;
import com.actiongraph.governance.spring.ActionGraphGovernanceAutoConfiguration;
import com.actiongraph.policy.ApprovalChain;
import com.actiongraph.policy.ApprovalChainResolver;
import com.actiongraph.policy.NoopReviewAttributeContributor;
import com.actiongraph.policy.ReviewAttributeContributor;
import com.actiongraph.policy.SingleStageApprovalChainResolver;
import com.actiongraph.runtime.Executor;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphGovernanceHumanReviewAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphGovernanceAutoConfiguration.class,
                    ActionGraphGovernanceHumanReviewAutoConfiguration.class
            ));

    private final ApplicationContextRunner runtimeCompositionRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphGovernanceAutoConfiguration.class,
                    ActionGraphGovernanceHumanReviewAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class
            ));

    @Test
    void defaultsToNeutralReviewContributorAndSingleStageApproval() {
        contextRunner.run(context -> {
            assertThat(context.getBean(ReviewAttributeContributor.class))
                    .isInstanceOf(NoopReviewAttributeContributor.class);
            assertThat(context.getBean(ApprovalChainResolver.class))
                    .isInstanceOf(SingleStageApprovalChainResolver.class);
        });
    }

    @Test
    void canEnableRiskBasedApprovalChainResolver() {
        contextRunner
                .withPropertyValues("actiongraph.human-review.risk-based-approval-chain=true")
                .run(context -> assertThat(context.getBean(ApprovalChainResolver.class))
                        .isInstanceOf(RiskBasedChainResolver.class));
    }

    @Test
    void canEnableAmountReviewAttributesFromLimitProperties() {
        contextRunner
                .withBean(AmountExtractor.class, () -> amount("100000.01", "CNY"))
                .withPropertyValues(
                        "actiongraph.limits.rules[0].action-id=sales.approval.request",
                        "actiongraph.limits.rules[0].currency=cny",
                        "actiongraph.limits.rules[0].hard-limit=1000000",
                        "actiongraph.limits.rules[0].review-limit=100000"
                )
                .run(context -> {
                    assertThat(context.getBean(ReviewAttributeContributor.class))
                            .isInstanceOf(AmountAttributeContributor.class);
                    ReviewAttributeContributor contributor = context.getBean(ReviewAttributeContributor.class);
                    assertThat(contributor.contribute(TestActions.action("sales.approval.request"), TestActions.blackboard()))
                            .containsExactlyInAnyOrderEntriesOf(Map.of(
                                    "amount", "100000.01",
                                    "currency", "CNY",
                                    "amountEscalated", "true"
                            ));
                });
    }

    @Test
    void applicationBeansCanOverrideHumanReviewGovernanceDefaults() {
        ReviewAttributeContributor contributor = (action, blackboard) -> Map.of("source", "custom");
        ApprovalChainResolver resolver = request -> ApprovalChain.single();

        contextRunner
                .withBean(ReviewAttributeContributor.class, () -> contributor)
                .withBean(ApprovalChainResolver.class, () -> resolver)
                .withPropertyValues(
                        "actiongraph.human-review.risk-based-approval-chain=true",
                        "actiongraph.limits.rules[0].action-id=*",
                        "actiongraph.limits.rules[0].currency=CNY",
                        "actiongraph.limits.rules[0].hard-limit=100",
                        "actiongraph.limits.rules[0].review-limit=50"
                )
                .run(context -> {
                    assertThat(context.getBean(ReviewAttributeContributor.class)).isSameAs(contributor);
                    assertThat(context.getBean(ApprovalChainResolver.class)).isSameAs(resolver);
                });
    }

    @Test
    void humanReviewGovernanceBeansComposeBeforeRuntimeStarterDefaults() {
        runtimeCompositionRunner
                .withPropertyValues(
                        "actiongraph.human-review.risk-based-approval-chain=true",
                        "actiongraph.limits.rules[0].action-id=sales.approval.request",
                        "actiongraph.limits.rules[0].currency=CNY",
                        "actiongraph.limits.rules[0].hard-limit=1000000",
                        "actiongraph.limits.rules[0].review-limit=100000"
                )
                .run(context -> {
                    assertThat(context.getBean(ApprovalChainResolver.class))
                            .isInstanceOf(RiskBasedChainResolver.class);
                    assertThat(context.getBean(ReviewAttributeContributor.class))
                            .isInstanceOf(AmountAttributeContributor.class);
                    assertThat(context).hasSingleBean(Executor.class);
                });
    }

    private AmountExtractor amount(String value, String currency) {
        return (action, blackboard) -> Optional.of(new MonetaryAmount(new BigDecimal(value), currency));
    }
}
