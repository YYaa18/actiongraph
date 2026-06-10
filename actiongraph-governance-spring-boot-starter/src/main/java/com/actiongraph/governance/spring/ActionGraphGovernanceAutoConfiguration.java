package com.actiongraph.governance.spring;

import com.actiongraph.governance.AmountAttributeContributor;
import com.actiongraph.governance.AmountExtractor;
import com.actiongraph.governance.AmountLimitPolicy;
import com.actiongraph.governance.AmountLimitRule;
import com.actiongraph.policy.ApprovalChainResolver;
import com.actiongraph.policy.DataMaskingPolicy;
import com.actiongraph.policy.DefaultPermissionPolicy;
import com.actiongraph.governance.NoopAmountExtractor;
import com.actiongraph.policy.NoopMaskingPolicy;
import com.actiongraph.policy.NoopReviewAttributeContributor;
import com.actiongraph.policy.PermissionPolicy;
import com.actiongraph.governance.RegexMaskingPolicy;
import com.actiongraph.policy.ReviewAttributeContributor;
import com.actiongraph.governance.RiskBasedChainResolver;
import com.actiongraph.policy.SingleStageApprovalChainResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration(beforeName = "com.actiongraph.spring.ActionGraphAutoConfiguration")
@ConditionalOnClass(name = "com.actiongraph.policy.DataMaskingPolicy")
@EnableConfigurationProperties(ActionGraphGovernanceProperties.class)
public class ActionGraphGovernanceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public AmountExtractor actionGraphAmountExtractor() {
        return NoopAmountExtractor.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionPolicy actionGraphPermissionPolicy(
            ActionGraphGovernanceProperties properties,
            AmountExtractor amountExtractor
    ) {
        List<AmountLimitRule> rules = properties.getLimits().toAmountLimitRules();
        if (rules.isEmpty()) {
            return new DefaultPermissionPolicy();
        }
        return new AmountLimitPolicy(amountExtractor, rules);
    }

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

    @Bean
    @ConditionalOnMissingBean
    public DataMaskingPolicy actionGraphDataMaskingPolicy(ActionGraphGovernanceProperties properties) {
        if (!properties.getMasking().isEnabled()) {
            return NoopMaskingPolicy.INSTANCE;
        }
        return RegexMaskingPolicy.builder()
                .addFinancialDefaults()
                .addBlockedKeys(properties.getMasking().getBlockedKeys())
                .build();
    }
}
