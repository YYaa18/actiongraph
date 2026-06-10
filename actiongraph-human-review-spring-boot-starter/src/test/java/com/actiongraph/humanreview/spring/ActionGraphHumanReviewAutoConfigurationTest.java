package com.actiongraph.humanreview.spring;

import com.actiongraph.policy.ApprovalChainResolver;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.HumanReviewRepository;
import com.actiongraph.policy.InMemoryHumanReviewRepository;
import com.actiongraph.policy.PendingHumanReviewPolicy;
import com.actiongraph.policy.RepositoryBackedHumanReviewPolicy;
import com.actiongraph.policy.SingleStageApprovalChainResolver;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphHumanReviewAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActionGraphHumanReviewAutoConfiguration.class));

    private final ApplicationContextRunner runtimeCompositionRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphHumanReviewAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class
            ));

    @Test
    void createsRepositoryBackedHumanReviewPolicy() {
        contextRunner.run(context -> {
            assertThat(context.getBean(HumanReviewRepository.class))
                    .isInstanceOf(InMemoryHumanReviewRepository.class);
            assertThat(context.getBean(ApprovalChainResolver.class))
                    .isInstanceOf(SingleStageApprovalChainResolver.class);
            assertThat(context.getBean(HumanReviewPolicy.class))
                    .isInstanceOf(RepositoryBackedHumanReviewPolicy.class);
        });
    }

    @Test
    void backsOffWhenApplicationProvidesHumanReviewBeans() {
        HumanReviewRepository repository = new InMemoryHumanReviewRepository();
        ApprovalChainResolver resolver = SingleStageApprovalChainResolver.INSTANCE;
        HumanReviewPolicy policy = new PendingHumanReviewPolicy();

        contextRunner
                .withBean(HumanReviewRepository.class, () -> repository)
                .withBean(ApprovalChainResolver.class, () -> resolver)
                .withBean(HumanReviewPolicy.class, () -> policy)
                .run(context -> {
                    assertThat(context.getBean(HumanReviewRepository.class)).isSameAs(repository);
                    assertThat(context.getBean(ApprovalChainResolver.class)).isSameAs(resolver);
                    assertThat(context.getBean(HumanReviewPolicy.class)).isSameAs(policy);
                });
    }

    @Test
    void repositoryBackedPolicyWinsOverRuntimeStarterPendingDefault() {
        runtimeCompositionRunner.run(context -> {
            assertThat(context.getBean(HumanReviewRepository.class))
                    .isInstanceOf(InMemoryHumanReviewRepository.class);
            assertThat(context.getBean(HumanReviewPolicy.class))
                    .isInstanceOf(RepositoryBackedHumanReviewPolicy.class);
        });
    }
}
