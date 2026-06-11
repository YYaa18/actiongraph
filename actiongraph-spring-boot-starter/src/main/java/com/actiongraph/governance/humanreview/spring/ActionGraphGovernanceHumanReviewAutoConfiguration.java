package com.actiongraph.governance.humanreview.spring;

import com.actiongraph.governance.AmountExtractor;
import com.actiongraph.governance.AmountLimitRule;
import com.actiongraph.governance.humanreview.AmountAttributeContributor;
import com.actiongraph.governance.humanreview.RiskBasedChainResolver;
import com.actiongraph.governance.spring.ActionGraphGovernanceProperties;
import com.actiongraph.policy.ApprovalChainResolver;
import com.actiongraph.policy.NoopReviewAttributeContributor;
import com.actiongraph.policy.ReviewAttributeContributor;
import com.actiongraph.policy.SingleStageApprovalChainResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration(
        beforeName = {
                "com.actiongraph.humanreview.spring.ActionGraphHumanReviewAutoConfiguration",
                "com.actiongraph.spring.ActionGraphAutoConfiguration"
        },
        afterName = "com.actiongraph.governance.spring.ActionGraphGovernanceAutoConfiguration"
)
@ConditionalOnClass(ApprovalChainResolver.class)
@EnableConfigurationProperties(ActionGraphGovernanceProperties.class)
public class ActionGraphGovernanceHumanReviewAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ReviewAttributeContributor actionGraphReviewAttributeContributor(
            ActionGraphGovernanceProperties properties,
            AmountExtractor amountExtractor
    ) {
        List<AmountLimitRule> rules = properties.getLimits().toAmountLimitRules();
        if (rules.isEmpty()) {
            return NoopReviewAttributeContributor.INSTANCE;
        }
        return new AmountAttributeContributor(amountExtractor, rules);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalChainResolver actionGraphApprovalChainResolver(ActionGraphGovernanceProperties properties) {
        return properties.getHumanReview().isRiskBasedApprovalChain()
                ? new RiskBasedChainResolver()
                : SingleStageApprovalChainResolver.INSTANCE;
    }
}
