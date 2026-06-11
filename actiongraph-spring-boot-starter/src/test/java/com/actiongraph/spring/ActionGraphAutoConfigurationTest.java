package com.actiongraph.spring;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionExecutionPolicy;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.action.annotation.ActionGraphAction;
import com.actiongraph.action.annotation.ActionGraphGuard;
import com.actiongraph.contribution.ActionGraphContribution;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalParameterDefinition;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.llm.LlmClient;
import com.actiongraph.llm.OpenAiCompatibleChatClient;
import com.actiongraph.observability.NoopObservationSink;
import com.actiongraph.observability.ObservationEvent;
import com.actiongraph.observability.ObservationEventType;
import com.actiongraph.observability.ObservationSink;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphAutoConfigurationTest {
    private static final Condition INPUT_PRESENT = Condition.of("spring-test:INPUT_PRESENT");
    private static final Condition LOADED = Condition.of("spring-test:LOADED");
    private static final Condition DONE = Condition.of("spring-test:DONE");
    private static final Condition DONE_TYPO = Condition.of("spring-test:DNOE");
    private static final GoalType TEST_GOAL_TYPE = new GoalType("spring-test.finish");

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
    void registersContributionActionsGoalsAndSeeders() {
        contextRunner
                .withBean("testContribution", ActionGraphContribution.class, ReachableContribution::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ActionRegistry.class).all())
                            .extracting(action -> action.id().value())
                            .contains("contribution.finish");
                    assertThat(context.getBean(GoalCatalog.class).byType(TEST_GOAL_TYPE)).isPresent();
                    assertThat(context.getBean(GoalBlackboardSeederRegistry.class).byGoalType(TEST_GOAL_TYPE))
                            .isPresent();
                });
    }

    @Test
    void failsFastWhenContributionsDeclareDuplicateActionIds() {
        contextRunner
                .withBean("firstContribution", ActionGraphContribution.class, FirstDuplicateContribution::new)
                .withBean("secondContribution", ActionGraphContribution.class, SecondDuplicateContribution::new)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Duplicate action id contribution.duplicate")
                            .hasMessageContaining(FirstDuplicateContribution.class.getName())
                            .hasMessageContaining(SecondDuplicateContribution.class.getName());
                });
    }

    @Test
    void validationFailsByDefaultWithActionLevelDiagnostics() {
        contextRunner
                .withBean("unreachableContribution", ActionGraphContribution.class, UnreachableContribution::new)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("ActionGraph validation failed")
                            .hasMessageContaining("missing condition(s) spring-test:DONE")
                            .hasMessageContaining("closest registered effect for spring-test:DONE is spring-test:DNOE")
                            .hasMessageContaining("dangling action(s)");
                });
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void validationWarnLogsAndAllowsStartup(CapturedOutput output) {
        contextRunner
                .withPropertyValues("actiongraph.validation.mode=WARN")
                .withBean("unreachableContribution", ActionGraphContribution.class, UnreachableContribution::new)
                .run(context -> assertThat(context).hasNotFailed());

        assertThat(output).contains("ActionGraph validation failed");
    }

    @Test
    void validationOffAllowsStartupWithoutDiagnostics() {
        contextRunner
                .withPropertyValues("actiongraph.validation.mode=OFF")
                .withBean("unreachableContribution", ActionGraphContribution.class, UnreachableContribution::new)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void executionPolicyPropertiesOverrideAutoRegisteredAnnotatedActions() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.execution.policies[0].action-id=spring-test.load",
                        "actiongraph.execution.policies[0].max-attempts=3",
                        "actiongraph.execution.policies[0].backoff=5ms",
                        "actiongraph.execution.policies[0].timeout=250ms"
                )
                .withBean(AnnotatedWorkflow.class)
                .run(context -> {
                    Action action = context.getBean(ActionRegistry.class)
                            .byId(new ActionId("spring-test.load"))
                            .orElseThrow();
                    ActionExecutionPolicy policy = action.executionPolicy();
                    assertThat(policy.maxAttempts()).isEqualTo(3);
                    assertThat(policy.backoff()).isEqualTo(Duration.ofMillis(5));
                    assertThat(policy.timeout()).isEqualTo(Duration.ofMillis(250));
                });
    }

    @Test
    void llmClientIsNotCreatedByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(LlmClient.class));
    }

    @Test
    void createsOpenAiCompatibleLlmClientFromEnvironmentVariableName() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.llm.provider=openai-compatible",
                        "actiongraph.llm.base-url=http://localhost:9999/v1/chat/completions",
                        "actiongraph.llm.model=qwen-max",
                        "actiongraph.llm.api-key-env=PATH",
                        "actiongraph.llm.headers.X-Gateway-AppId=actiongraph"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(OpenAiCompatibleChatClient.class);
                });
    }

    @Test
    void rejectsPlaintextLlmApiKeyConfiguration() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.llm.provider=openai-compatible",
                        "actiongraph.llm.base-url=http://localhost:9999/v1/chat/completions",
                        "actiongraph.llm.model=qwen-max",
                        "actiongraph.llm.api-key=plain-secret"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()).getMessage()).contains("api-key-env");
                });
    }

    @Test
    void failsWhenConfiguredLlmApiKeyEnvironmentVariableIsMissing() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.llm.provider=openai-compatible",
                        "actiongraph.llm.base-url=http://localhost:9999/v1/chat/completions",
                        "actiongraph.llm.model=qwen-max",
                        "actiongraph.llm.api-key-env=ACTIONGRAPH_TEST_ENV_THAT_SHOULD_NOT_EXIST"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("ACTIONGRAPH_TEST_ENV_THAT_SHOULD_NOT_EXIST")
                            .hasMessageContaining("api-key-env");
                });
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
    void createsNoopObservationSinkByDefault() {
        contextRunner.run(context -> assertThat(context.getBean(ObservationSink.class))
                .isSameAs(NoopObservationSink.INSTANCE));
    }

    @Test
    void usesApplicationObservationSinkInExecutor() {
        List<ObservationEvent> events = new ArrayList<>();

        contextRunner
                .withBean(ObservationSink.class, () -> events::add)
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
                    assertThat(events)
                            .extracting(ObservationEvent::type)
                            .contains(ObservationEventType.ACTION_FINISHED, ObservationEventType.RUN_FINISHED);
                });
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

    static final class ReachableContribution implements ActionGraphContribution {
        @Override
        public List<Action> actions() {
            return List.of(new NoopAction("contribution.finish", Set.of(INPUT_PRESENT), Set.of(DONE)));
        }

        @Override
        public List<GoalDefinition> goals() {
            return List.of(testGoal(Set.of(INPUT_PRESENT), DONE));
        }

        @Override
        public List<GoalBlackboardSeeder> seeders() {
            return List.of(new TestSeeder());
        }
    }

    static final class UnreachableContribution implements ActionGraphContribution {
        @Override
        public List<Action> actions() {
            return List.of(new NoopAction("contribution.finish", Set.of(LOADED), Set.of(DONE_TYPO)));
        }

        @Override
        public List<GoalDefinition> goals() {
            return List.of(testGoal(Set.of(INPUT_PRESENT), DONE));
        }
    }

    static final class FirstDuplicateContribution implements ActionGraphContribution {
        @Override
        public List<Action> actions() {
            return List.of(new NoopAction("contribution.duplicate"));
        }
    }

    static final class SecondDuplicateContribution implements ActionGraphContribution {
        @Override
        public List<Action> actions() {
            return List.of(new NoopAction("contribution.duplicate"));
        }
    }

    static final class TestSeeder implements GoalBlackboardSeeder {
        @Override
        public GoalType goalType() {
            return TEST_GOAL_TYPE;
        }

        @Override
        public void seed(GoalParameters parameters, com.actiongraph.runtime.Blackboard blackboard) {
            blackboard.addCondition(INPUT_PRESENT);
        }
    }

    private static GoalDefinition testGoal(Set<Condition> seedConditions, Condition target) {
        return new GoalDefinition(
                TEST_GOAL_TYPE,
                "Finish the Spring test workflow.",
                new Goal("finishSpringWorkflow", Set.of(target)),
                List.of(GoalParameterDefinition.optional("inputId", "Input identifier.", "I-1")),
                seedConditions
        );
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record InputId(String value) {
    }

    private record LoadedRecord(String value) {
    }

    private static final class NoopAction implements Action {
        private final ActionId id;
        private final Set<Condition> preconditions;
        private final Set<Condition> effects;

        private NoopAction(String id) {
            this(id, Set.of(), Set.of());
        }

        private NoopAction(String id, Set<Condition> preconditions, Set<Condition> effects) {
            this.id = new ActionId(id);
            this.preconditions = preconditions;
            this.effects = effects;
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
            return preconditions;
        }

        @Override
        public Set<Condition> effects() {
            return effects;
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
