package com.actiongraph.humanreview.spring;

import com.actiongraph.policy.ApprovalChainResolver;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.HumanReviewRepository;
import com.actiongraph.policy.InMemoryHumanReviewRepository;
import com.actiongraph.policy.RepositoryBackedHumanReviewPolicy;
import com.actiongraph.policy.SingleStageApprovalChainResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
        beforeName = "com.actiongraph.spring.ActionGraphAutoConfiguration",
        afterName = "com.actiongraph.governance.spring.ActionGraphGovernanceAutoConfiguration"
)
@ConditionalOnClass(HumanReviewRepository.class)
public class ActionGraphHumanReviewAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public HumanReviewRepository actionGraphHumanReviewRepository() {
        return new InMemoryHumanReviewRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalChainResolver actionGraphApprovalChainResolver() {
        return SingleStageApprovalChainResolver.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    public HumanReviewPolicy actionGraphHumanReviewPolicy(
            HumanReviewRepository humanReviewRepository,
            ApprovalChainResolver approvalChainResolver
    ) {
        return new RepositoryBackedHumanReviewPolicy(humanReviewRepository, approvalChainResolver);
    }
}
