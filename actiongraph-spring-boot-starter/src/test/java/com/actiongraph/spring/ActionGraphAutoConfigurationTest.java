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
import com.actiongraph.memory.InMemoryMemoryRepository;
import com.actiongraph.memory.MemoryContextLoader;
import com.actiongraph.memory.MemoryRepository;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.policy.DataMaskingPolicy;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.HumanReviewRepository;
import com.actiongraph.policy.InMemoryHumanReviewRepository;
import com.actiongraph.policy.NoopMaskingPolicy;
import com.actiongraph.policy.RepositoryBackedHumanReviewPolicy;
import com.actiongraph.runtime.Executor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.Map;
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
    void createsRepositoryBackedHumanReviewPolicyByDefault() {
        contextRunner.run(context -> {
            assertThat(context.getBean(HumanReviewRepository.class))
                    .isInstanceOf(InMemoryHumanReviewRepository.class);
            assertThat(context.getBean(HumanReviewPolicy.class))
                    .isInstanceOf(RepositoryBackedHumanReviewPolicy.class);
        });
    }

    @Test
    void createsMemoryRepositoryAndContextLoaderByDefault() {
        contextRunner.run(context -> {
            assertThat(context.getBean(MemoryRepository.class))
                    .isInstanceOf(InMemoryMemoryRepository.class);
            assertThat(context.getBean(MemoryContextLoader.class)).isNotNull();
        });
    }

    @Test
    void bindsPersistenceClaimTimeoutProperty() {
        contextRunner
                .withPropertyValues("actiongraph.persistence.suspended-run-claim-timeout=2m")
                .run(context -> assertThat(context.getBean(ActionGraphProperties.class)
                        .getPersistence()
                        .getSuspendedRunClaimTimeout()).isEqualTo(Duration.ofMinutes(2)));
    }

    @Test
    void createsNoopMaskingPolicyByDefault() {
        contextRunner.run(context -> assertThat(context.getBean(DataMaskingPolicy.class))
                .isInstanceOf(NoopMaskingPolicy.class));
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
