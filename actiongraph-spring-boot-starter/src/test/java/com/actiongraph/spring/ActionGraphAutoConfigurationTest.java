package com.actiongraph.spring;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.action.annotation.ActionGraphAction;
import com.actiongraph.action.annotation.ActionGraphGuard;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.policy.DataMaskingPolicy;
import com.actiongraph.policy.DefaultPermissionPolicy;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.NoopMaskingPolicy;
import com.actiongraph.policy.NoopReviewAttributeContributor;
import com.actiongraph.policy.PendingHumanReviewPolicy;
import com.actiongraph.policy.PermissionPolicy;
import com.actiongraph.policy.ReviewAttributeContributor;
import com.actiongraph.runtime.Executor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphAutoConfigurationTest {
    private static final Condition INPUT_PRESENT = Condition.of("spring-test:INPUT_PRESENT");
    private static final Condition LOADED = Condition.of("spring-test:LOADED");
    private static final Condition DONE = Condition.of("spring-test:DONE");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActionGraphAutoConfiguration.class));

    @Test
    void createsRegistryFromAnnotatedSpringBeans() {
        contextRunner
                .withBean(AnnotatedWorkflow.class)
                .run(context -> {
                    ActionRegistry registry = context.getBean(ActionRegistry.class);

                    assertThat(registry.all())
                            .extracting(action -> action.id().value())
                            .containsExactly("spring-test.finish", "spring-test.load");
                });
    }

    @Test
    void executorRunsAnnotatedSpringBeanActions() {
        contextRunner
                .withBean(AnnotatedWorkflow.class)
                .run(context -> {
                    ActionRegistry registry = context.getBean(ActionRegistry.class);
                    Executor executor = context.getBean(Executor.class);
                    InMemoryBlackboard blackboard = new InMemoryBlackboard();
                    blackboard.put(new InputId("I-1"));
                    blackboard.addCondition(INPUT_PRESENT);

                    var result = executor.run(
                            new Goal("finishSpringWorkflow", Set.of(DONE)),
                            blackboard,
                            registry.all(),
                            registry
                    );

                    assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
                    assertThat(result.executedActions())
                            .extracting(ActionId::value)
                            .containsExactly("spring-test.load", "spring-test.finish");
                    assertThat(blackboard.get(LoadedRecord.class)).contains(new LoadedRecord("I-1"));
                });
    }

    @Test
    void associatesRuntimeGuardsFromDifferentSpringBeans() {
        contextRunner
                .withBean(ActionOnlyWorkflow.class)
                .withBean(DenyFinishGuard.class)
                .run(context -> {
                    ActionRegistry registry = context.getBean(ActionRegistry.class);
                    Executor executor = context.getBean(Executor.class);
                    InMemoryBlackboard blackboard = new InMemoryBlackboard();
                    blackboard.put(new InputId("I-1"));
                    blackboard.addCondition(INPUT_PRESENT);

                    var result = executor.run(
                            new Goal("finishSpringWorkflow", Set.of(DONE)),
                            blackboard,
                            registry.all(),
                            registry
                    );

                    assertThat(result.status()).isEqualTo(RunStatus.HALTED_UNREACHABLE);
                    assertThat(result.executedActions())
                            .extracting(ActionId::value)
                            .containsExactly("spring-test.load");
                });
    }

    @Test
    void backsOffWhenApplicationProvidesActionRegistry() {
        DefaultActionRegistry customRegistry = new DefaultActionRegistry();

        contextRunner
                .withBean(ActionRegistry.class, () -> customRegistry)
                .withBean(AnnotatedWorkflow.class)
                .run(context -> assertThat(context.getBean(ActionRegistry.class)).isSameAs(customRegistry));
    }

    @Test
    void canDisableAnnotatedBeanRegistration() {
        contextRunner
                .withPropertyValues("actiongraph.actions.auto-register-annotated=false")
                .withBean(AnnotatedWorkflow.class)
                .run(context -> assertThat(context.getBean(ActionRegistry.class).all()).isEmpty());
    }

    @Test
    void alsoRegistersExplicitActionBeans() {
        contextRunner
                .withBean(Action.class, () -> new NoopAction("manual.action"))
                .run(context -> assertThat(context.getBean(ActionRegistry.class).all())
                        .extracting(action -> action.id().value())
                        .containsExactly("manual.action"));
    }

    @Test
    void createsSafePendingHumanReviewPolicyByDefaultWithoutReviewStorage() {
        contextRunner.run(context -> {
            assertThat(context.getBean(HumanReviewPolicy.class))
                    .isInstanceOf(PendingHumanReviewPolicy.class);
            assertThat(context.containsBean("actionGraphHumanReviewRepository")).isFalse();
            assertThat(context.containsBean("actionGraphApprovalChainResolver")).isFalse();
        });
    }

    @Test
    void doesNotCreateMemoryBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context.containsBean("actionGraphMemoryRepository")).isFalse();
            assertThat(context.containsBean("actionGraphMemoryContextLoader")).isFalse();
        });
    }

    @Test
    void createsNoopMaskingPolicyByDefault() {
        contextRunner.run(context -> assertThat(context.getBean(DataMaskingPolicy.class))
                .isInstanceOf(NoopMaskingPolicy.class));
    }

    @Test
    void createsDefaultGovernanceBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context.containsBean("actionGraphAmountExtractor")).isFalse();
            assertThat(context.getBean(PermissionPolicy.class))
                    .isInstanceOf(DefaultPermissionPolicy.class);
            assertThat(context.getBean(ReviewAttributeContributor.class))
                    .isInstanceOf(NoopReviewAttributeContributor.class);
        });
    }

    @Test
    void governancePropertiesAreIgnoredWithoutGovernanceStarter() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.masking.enabled=true",
                        "actiongraph.human-review.risk-based-approval-chain=true",
                        "actiongraph.limits.rules[0].action-id=sales.approval.request",
                        "actiongraph.limits.rules[0].currency=CNY",
                        "actiongraph.limits.rules[0].hard-limit=1000000",
                        "actiongraph.limits.rules[0].review-limit=100000"
                )
                .run(context -> {
                    assertThat(context.getBean(DataMaskingPolicy.class))
                            .isInstanceOf(NoopMaskingPolicy.class);
                    assertThat(context.getBean(PermissionPolicy.class))
                            .isInstanceOf(DefaultPermissionPolicy.class);
                    assertThat(context.getBean(ReviewAttributeContributor.class))
                            .isInstanceOf(NoopReviewAttributeContributor.class);
                    assertThat(context.getBean(HumanReviewPolicy.class))
                            .isInstanceOf(PendingHumanReviewPolicy.class);
                    assertThat(context.containsBean("actionGraphAmountExtractor")).isFalse();
                    assertThat(context.containsBean("actionGraphApprovalChainResolver")).isFalse();
                });
    }

    static final class AnnotatedWorkflow {
        @ActionGraphAction(
                id = "spring-test.load",
                preconditions = "spring-test:INPUT_PRESENT",
                effects = "spring-test:LOADED"
        )
        LoadedRecord load(InputId inputId) {
            return new LoadedRecord(inputId.value());
        }

        @ActionGraphGuard(actionId = "spring-test.finish")
        boolean canFinish(LoadedRecord loadedRecord) {
            return loadedRecord.value() != null && !loadedRecord.value().isBlank();
        }

        @ActionGraphAction(
                id = "spring-test.finish",
                preconditions = "spring-test:LOADED",
                effects = "spring-test:DONE"
        )
        ActionResult finish(LoadedRecord loadedRecord) {
            return ActionResult.ok();
        }
    }

    static final class ActionOnlyWorkflow {
        @ActionGraphAction(
                id = "spring-test.load",
                preconditions = "spring-test:INPUT_PRESENT",
                effects = "spring-test:LOADED"
        )
        LoadedRecord load(InputId inputId) {
            return new LoadedRecord(inputId.value());
        }

        @ActionGraphAction(
                id = "spring-test.finish",
                preconditions = "spring-test:LOADED",
                effects = "spring-test:DONE"
        )
        ActionResult finish(LoadedRecord loadedRecord) {
            return ActionResult.ok();
        }
    }

    static final class DenyFinishGuard {
        @ActionGraphGuard(actionId = "spring-test.finish")
        boolean deny(LoadedRecord loadedRecord) {
            return false;
        }
    }

    private record InputId(String value) {
    }

    private record LoadedRecord(String value) {
    }

    private static final class NoopAction implements Action {
        private final ActionId id;

        private NoopAction(String id) {
            this.id = new ActionId(id);
        }

        @Override
        public ActionId id() {
            return id;
        }

        @Override
        public Set<Class<?>> inputTypes() {
            return Set.of();
        }

        @Override
        public Set<Class<?>> outputTypes() {
            return Set.of();
        }

        @Override
        public Set<Condition> preconditions() {
            return Set.of();
        }

        @Override
        public Set<Condition> effects() {
            return Set.of();
        }

        @Override
        public int cost() {
            return 1;
        }

        @Override
        public ActionRiskLevel riskLevel() {
            return ActionRiskLevel.LOW;
        }

        @Override
        public boolean requiresHumanReview() {
            return false;
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            return ActionResult.ok();
        }
    }
}
