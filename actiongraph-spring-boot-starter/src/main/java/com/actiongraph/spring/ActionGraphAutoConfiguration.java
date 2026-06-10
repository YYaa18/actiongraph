package com.actiongraph.spring;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.memory.InMemoryMemoryRepository;
import com.actiongraph.memory.MemoryContextLoader;
import com.actiongraph.memory.MemoryRepository;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.planning.Planner;
import com.actiongraph.policy.ApprovalChainResolver;
import com.actiongraph.policy.DataMaskingPolicy;
import com.actiongraph.policy.DefaultPermissionPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.ExecutionPolicyGuard;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.HumanReviewRepository;
import com.actiongraph.policy.InMemoryHumanReviewRepository;
import com.actiongraph.policy.NoopMaskingPolicy;
import com.actiongraph.policy.NoopReviewAttributeContributor;
import com.actiongraph.policy.PermissionPolicy;
import com.actiongraph.policy.RepositoryBackedHumanReviewPolicy;
import com.actiongraph.policy.ReviewAttributeContributor;
import com.actiongraph.policy.SingleStageApprovalChainResolver;
import com.actiongraph.runtime.Executor;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemorySuspendedRunRepository;
import com.actiongraph.runtime.SuspendedRunRepository;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ActionGraphProperties.class)
public class ActionGraphAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public Planner actionGraphPlanner(ActionGraphProperties properties) {
        return new GoapPlanner(
                properties.getPlanner().getMaxDepth(),
                properties.getPlanner().getMaxExpansions()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceRepository actionGraphTraceRepository() {
        return new InMemoryTraceRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public SuspendedRunRepository actionGraphSuspendedRunRepository() {
        return new InMemorySuspendedRunRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionPolicy actionGraphPermissionPolicy() {
        return new DefaultPermissionPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutionPolicyGuard actionGraphPolicyGuard(PermissionPolicy permissionPolicy) {
        return new DefaultPolicyGuard(permissionPolicy);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReviewAttributeContributor actionGraphReviewAttributeContributor() {
        return NoopReviewAttributeContributor.INSTANCE;
    }

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

    @Bean
    @ConditionalOnMissingBean
    public MemoryRepository actionGraphMemoryRepository() {
        return new InMemoryMemoryRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryContextLoader actionGraphMemoryContextLoader(MemoryRepository memoryRepository) {
        return new MemoryContextLoader(memoryRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataMaskingPolicy actionGraphDataMaskingPolicy() {
        return NoopMaskingPolicy.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(Executor.class)
    public GoapExecutor actionGraphExecutor(
            Planner planner,
            ExecutionPolicyGuard policyGuard,
            HumanReviewPolicy humanReviewPolicy,
            TraceRepository traceRepository,
            SuspendedRunRepository suspendedRunRepository,
            DataMaskingPolicy maskingPolicy,
            ReviewAttributeContributor reviewAttributeContributor,
            ActionGraphProperties properties
    ) {
        return GoapExecutor.builder()
                .planner(planner)
                .policyGuard(policyGuard)
                .humanReviewPolicy(humanReviewPolicy)
                .traceRepository(traceRepository)
                .suspendedRunRepository(suspendedRunRepository)
                .maskingPolicy(maskingPolicy)
                .reviewAttributeContributor(reviewAttributeContributor)
                .maxSteps(properties.getExecutor().getMaxSteps())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionRegistry actionGraphActionRegistry(
            ObjectProvider<Action> actionBeans,
            ConfigurableListableBeanFactory beanFactory,
            ActionGraphProperties properties
    ) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        actionBeans.orderedStream().forEach(registry::register);
        if (properties.getActions().isAutoRegisterAnnotated()) {
            new AnnotatedSpringBeanActionRegistrar(beanFactory).registerAnnotatedActions(registry);
        }
        return registry;
    }
}
