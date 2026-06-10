package com.actiongraph.governance.spring;

import com.actiongraph.governance.AmountAttributeContributor;
import com.actiongraph.governance.AmountExtractor;
import com.actiongraph.governance.AmountLimitPolicy;
import com.actiongraph.policy.ApprovalChainResolver;
import com.actiongraph.policy.DataMaskingPolicy;
import com.actiongraph.policy.DefaultPermissionPolicy;
import com.actiongraph.governance.NoopAmountExtractor;
import com.actiongraph.policy.NoopMaskingPolicy;
import com.actiongraph.policy.NoopReviewAttributeContributor;
import com.actiongraph.policy.PermissionPolicy;
import com.actiongraph.policy.ReviewAttributeContributor;
import com.actiongraph.governance.RiskBasedChainResolver;
import com.actiongraph.policy.SingleStageApprovalChainResolver;
import com.actiongraph.action.Action;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.Executor;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphGovernanceAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActionGraphGovernanceAutoConfiguration.class));

    private final ApplicationContextRunner runtimeCompositionRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphGovernanceAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class
            ));

    @Test
    void governanceDefaultsAreNeutral() {
        contextRunner.run(context -> {
            assertThat(context.getBean(AmountExtractor.class))
                    .isInstanceOf(NoopAmountExtractor.class);
            assertThat(context.getBean(PermissionPolicy.class))
                    .isInstanceOf(DefaultPermissionPolicy.class);
            assertThat(context.getBean(ReviewAttributeContributor.class))
                    .isInstanceOf(NoopReviewAttributeContributor.class);
            assertThat(context.getBean(ApprovalChainResolver.class))
                    .isInstanceOf(SingleStageApprovalChainResolver.class);
            assertThat(context.getBean(DataMaskingPolicy.class))
                    .isInstanceOf(NoopMaskingPolicy.class);
        });
    }

    @Test
    void createsRegexMaskingPolicyWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.masking.enabled=true",
                        "actiongraph.masking.blocked-keys=customerName"
                )
                .run(context -> {
                    DataMaskingPolicy policy = context.getBean(DataMaskingPolicy.class);

                    assertThat(policy.maskText("13812345678")).isEqualTo("138****5678");
                    assertThat(policy.maskData(Map.of("customerName", "张三")))
                            .containsEntry("customerName", "***");
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
    void canEnableAmountLimitPolicyFromProperties() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.limits.rules[0].action-id=sales.approval.request",
                        "actiongraph.limits.rules[0].currency=cny",
                        "actiongraph.limits.rules[0].hard-limit=1000000",
                        "actiongraph.limits.rules[0].review-limit=100000"
                )
                .run(context -> {
                    assertThat(context.getBean(PermissionPolicy.class))
                            .isInstanceOf(AmountLimitPolicy.class);
                    assertThat(context.getBean(ReviewAttributeContributor.class))
                            .isInstanceOf(AmountAttributeContributor.class);
                    assertThat(context.getBean(ActionGraphGovernanceProperties.class)
                            .getLimits()
                            .getRules()
                            .getFirst()
                            .getCurrency()).isEqualTo("cny");
                });
    }

    @Test
    void applicationBeansCanOverrideGovernanceDefaults() {
        DataMaskingPolicy maskingPolicy = new DataMaskingPolicy() {
            @Override
            public String maskText(String text) {
                return "custom";
            }

            @Override
            public Map<String, String> maskData(Map<String, String> data) {
                return Map.of("custom", "true");
            }
        };
        PermissionPolicy permissionPolicy = new PermissionPolicy() {
            @Override
            public boolean canExecute(Action action, Blackboard blackboard) {
                return false;
            }
        };

        contextRunner
                .withBean(DataMaskingPolicy.class, () -> maskingPolicy)
                .withBean(PermissionPolicy.class, () -> permissionPolicy)
                .withPropertyValues(
                        "actiongraph.masking.enabled=true",
                        "actiongraph.limits.rules[0].action-id=*",
                        "actiongraph.limits.rules[0].currency=CNY",
                        "actiongraph.limits.rules[0].hard-limit=100",
                        "actiongraph.limits.rules[0].review-limit=50"
                )
                .run(context -> {
                    assertThat(context.getBean(DataMaskingPolicy.class)).isSameAs(maskingPolicy);
                    assertThat(context.getBean(PermissionPolicy.class)).isSameAs(permissionPolicy);
                    assertThat(context.getBean(ReviewAttributeContributor.class))
                            .isInstanceOf(AmountAttributeContributor.class);
                });
    }

    @Test
    void governanceBeansComposeBeforeRuntimeStarterDefaults() {
        runtimeCompositionRunner
                .withPropertyValues(
                        "actiongraph.masking.enabled=true",
                        "actiongraph.human-review.risk-based-approval-chain=true",
                        "actiongraph.limits.rules[0].action-id=sales.approval.request",
                        "actiongraph.limits.rules[0].currency=CNY",
                        "actiongraph.limits.rules[0].hard-limit=1000000",
                        "actiongraph.limits.rules[0].review-limit=100000"
                )
                .run(context -> {
                    assertThat(context.getBean(DataMaskingPolicy.class).maskText("13812345678"))
                            .isEqualTo("138****5678");
                    assertThat(context.getBean(ApprovalChainResolver.class))
                            .isInstanceOf(RiskBasedChainResolver.class);
                    assertThat(context.getBean(PermissionPolicy.class))
                            .isInstanceOf(AmountLimitPolicy.class);
                    assertThat(context.getBean(ReviewAttributeContributor.class))
                            .isInstanceOf(AmountAttributeContributor.class);
                    assertThat(context).hasSingleBean(Executor.class);
                });
    }
}
