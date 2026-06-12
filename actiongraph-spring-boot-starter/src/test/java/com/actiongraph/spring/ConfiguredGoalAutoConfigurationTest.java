package com.actiongraph.spring;

import com.actiongraph.ActionGraph;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.annotation.ActionGraphAction;
import com.actiongraph.action.annotation.BlackboardValue;
import com.actiongraph.fingerprint.ActionGraphFingerprints;
import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.annotation.ActionGraphGoal;
import com.actiongraph.interpretation.annotation.GoalValueConverterResolver;
import com.actiongraph.interpretation.annotation.GoalParameterBindingContext;
import com.actiongraph.interpretation.annotation.TypedGoalValueConverter;
import com.actiongraph.interpretation.config.ConfiguredGoalDefinition;
import com.actiongraph.interpretation.config.ConfiguredGoalDefinitionFactory;
import com.actiongraph.interpretation.config.ConfiguredGoalParameter;
import com.actiongraph.runtime.RunStatus;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredGoalAutoConfigurationTest {
    private static final String CONFIGURED_GOAL = "config.finish";
    private static final String INPUT_PRESENT = "config:INPUT_PRESENT";
    private static final String DONE = "config:DONE";
    private static final String INPUT_TYPE = InputId.class.getName();
    private static final String BUNDLE_GOAL = "bundle.finish";
    private static final String BUNDLE_START = "bundle:START";
    private static final String BUNDLE_DONE = "bundle:DONE";

    @TempDir
    Path tempDir;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActionGraphAutoConfiguration.class))
            .withBean(ConfiguredWorkflow.class)
            .withBean(InputIdConverter.class)
            .withPropertyValues(
                    "actiongraph.goals.definitions[0].type=" + CONFIGURED_GOAL,
                    "actiongraph.goals.definitions[0].description=Finish from external goal configuration.",
                    "actiongraph.goals.definitions[0].target-conditions[0]=" + DONE,
                    "actiongraph.goals.definitions[0].seed-conditions[0]=" + INPUT_PRESENT,
                    "actiongraph.goals.definitions[0].parameters[0].name=inputId",
                    "actiongraph.goals.definitions[0].parameters[0].type=" + INPUT_TYPE,
                    "actiongraph.goals.definitions[0].parameters[0].description=Input identifier",
                    "actiongraph.goals.definitions[0].parameters[0].example=I-7"
            );

    @Test
    void configuredGoalRunsEndToEndAndWritesFingerprintsToTrace() {
        contextRunner.run(context -> {
            ActionGraph actionGraph = context.getBean(ActionGraph.class);

            var result = actionGraph.start(CONFIGURED_GOAL, Map.of("inputId", "I-7"));

            assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
            var started = context.getBean(TraceRepository.class)
                    .findByRun(result.runId())
                    .stream()
                    .filter(event -> event.type() == TraceEventType.RUN_STARTED)
                    .findFirst()
                    .orElseThrow();
            assertThat(started.data())
                    .containsKeys("goalFingerprint", "actionGraphFingerprint")
                    .containsEntry("actionGraphFingerprint", actionGraph.actionGraphFingerprint());
            assertThat(started.data().get("goalFingerprint"))
                    .isEqualTo(actionGraph.goalFingerprint(CONFIGURED_GOAL).orElseThrow());
        });
    }

    @Test
    void configuredGoalDuplicateTypeReportsBothSources() {
        contextRunner
                .withBean(AnnotatedDuplicateGoal.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Duplicate goal type " + CONFIGURED_GOAL)
                            .hasMessageContaining("annotated Spring bean")
                            .hasMessageContaining("actiongraph.goals.definitions[0]");
                });
    }

    @Test
    void configuredGoalUnknownParameterTypeFailsAtStartup() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ActionGraphAutoConfiguration.class))
                .withBean(ConfiguredWorkflow.class)
                .withPropertyValues(
                        "actiongraph.goals.definitions[0].type=" + CONFIGURED_GOAL,
                        "actiongraph.goals.definitions[0].target-conditions[0]=" + DONE,
                        "actiongraph.goals.definitions[0].seed-conditions[0]=" + INPUT_PRESENT,
                        "actiongraph.goals.definitions[0].parameters[0].name=inputId",
                        "actiongraph.goals.definitions[0].parameters[0].type=com.example.DoesNotExist"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("unknown parameter type")
                            .hasMessageContaining("actiongraph.goals.definitions[0]");
                });
    }

    @Test
    void unreachableConfiguredGoalFailsStartupValidation() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ActionGraphAutoConfiguration.class))
                .withBean(ConfiguredWorkflow.class)
                .withPropertyValues(
                        "actiongraph.goals.definitions[0].type=" + CONFIGURED_GOAL,
                        "actiongraph.goals.definitions[0].target-conditions[0]=config:MISSING",
                        "actiongraph.goals.definitions[0].seed-conditions[0]=" + INPUT_PRESENT,
                        "actiongraph.goals.definitions[0].parameters[0].name=inputId",
                        "actiongraph.goals.definitions[0].parameters[0].type=string"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("ActionGraph validation failed")
                            .hasMessageContaining("goal '" + CONFIGURED_GOAL + "' unreachable")
                            .hasMessageContaining("config:MISSING");
                });
    }

    @Test
    void disabledConfiguredGoalIsNotRegisteredOrValidated() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ActionGraphAutoConfiguration.class))
                .withPropertyValues(
                        "actiongraph.goals.definitions[0].type=disabled.goal",
                        "actiongraph.goals.definitions[0].enabled=false",
                        "actiongraph.goals.definitions[0].target-conditions[0]=config:MISSING"
                )
                .run(context -> assertThat(context.getBean(GoalCatalog.class)
                        .byType(new GoalType("disabled.goal"))).isEmpty());
    }

    @Test
    void goalBundleImportsAndRunsWhenFingerprintsMatch() throws Exception {
        Path bundle = writeBundle("matching.yml", BundleWorkflow.class, bundleDefinition());

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ActionGraphAutoConfiguration.class))
                .withBean(BundleWorkflow.class)
                .withPropertyValues("actiongraph.goals.bundle.locations=" + bundle.toUri())
                .run(context -> {
                    ActionGraph actionGraph = context.getBean(ActionGraph.class);

                    var result = actionGraph.start(BUNDLE_GOAL, Map.of("ticketId", "T-1"));

                    assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
                    assertThat(context.getBean(GoalCatalog.class)
                            .byType(new GoalType(BUNDLE_GOAL))).isPresent();
                });
    }

    @Test
    void goalBundleFingerprintDriftFailsByDefault() throws Exception {
        Path bundle = writeBundle("drift-fail.yml", BundleWorkflow.class, bundleDefinition());

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ActionGraphAutoConfiguration.class))
                .withBean(BundleWorkflowDriftedRisk.class)
                .withPropertyValues("actiongraph.goals.bundle.locations=" + bundle.toUri())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("validated against action graph fingerprint")
                            .hasMessageContaining("current fingerprint");
                });
    }

    @Test
    void goalBundleFingerprintDriftWarnsAndStillValidatesReachabilityWhenConfigured() throws Exception {
        Path bundle = writeBundle("drift-warn.yml", BundleWorkflow.class, bundleDefinition());

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ActionGraphAutoConfiguration.class))
                .withBean(BundleWorkflowDriftedRisk.class)
                .withPropertyValues(
                        "actiongraph.goals.bundle.locations=" + bundle.toUri(),
                        "actiongraph.goals.bundle.fingerprint-mismatch=WARN"
                )
                .run(context -> {
                    ActionGraph actionGraph = context.getBean(ActionGraph.class);

                    var result = actionGraph.start(BUNDLE_GOAL, Map.of("ticketId", "T-1"));

                    assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
                });
    }

    @Test
    void goalBundleContentTamperingFailsSelfCheck() throws Exception {
        Path bundle = writeBundle("tampered.yml", BundleWorkflow.class, bundleDefinition());
        Files.writeString(bundle, Files.readString(bundle)
                .replace(BUNDLE_DONE, "bundle:TAMPERED"));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ActionGraphAutoConfiguration.class))
                .withBean(BundleWorkflow.class)
                .withPropertyValues("actiongraph.goals.bundle.locations=" + bundle.toUri())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("fingerprint self-check failed");
                });
    }

    private Path writeBundle(String filename, Class<?> workflowClass, ConfiguredGoalDefinition definition)
            throws Exception {
        ConfiguredGoalDefinitionFactory factory =
                new ConfiguredGoalDefinitionFactory(GoalValueConverterResolver.reflection());
        List<GoalDefinition> definitions = List.of(factory.definition(definition));
        String bundleFingerprint = ActionGraphFingerprints.goals(definitions);
        String actionGraphFingerprint = ActionGraphFingerprints.actionGraph(
                com.actiongraph.action.annotation.AnnotatedActionFactory.actions(newInstance(workflowClass)));
        Path path = tempDir.resolve(filename);
        Files.writeString(path, """
                actiongraph-bundle:
                  bundle-fingerprint: "%s"
                  action-graph-fingerprint: "%s"
                  validated-at: "2026-06-12T00:00:00Z"
                  approved-by: "tester"
                  source-env: "test"
                  definitions:
                    - type: "%s"
                      description: "Finish from a promoted bundle."
                      target-conditions:
                        - "%s"
                      seed-conditions:
                        - "%s"
                      parameters:
                        - name: "ticketId"
                          type: "string"
                          required: true
                          description: "Ticket identifier"
                          example: "T-1"
                """.formatted(
                bundleFingerprint,
                actionGraphFingerprint,
                definition.type(),
                definition.targetConditions().get(0),
                definition.seedConditions().get(0)
        ));
        return path;
    }

    private ConfiguredGoalDefinition bundleDefinition() {
        return new ConfiguredGoalDefinition(
                BUNDLE_GOAL,
                "Finish from a promoted bundle.",
                true,
                List.of(BUNDLE_DONE),
                List.of(BUNDLE_START),
                List.of(new ConfiguredGoalParameter("ticketId", "string", true,
                        "Ticket identifier", "T-1")),
                "test bundle"
        );
    }

    private Object newInstance(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static final class ConfiguredWorkflow {
        @ActionGraphAction(
                id = "config.finish",
                preconditions = INPUT_PRESENT,
                effects = DONE,
                description = "Finish a configured goal."
        )
        ActionResult finish(@BlackboardValue("inputId") InputId inputId) {
            return ActionResult.ok();
        }
    }

    static final class AnnotatedDuplicateGoal {
        @ActionGraphGoal(type = CONFIGURED_GOAL, targetConditions = DONE)
        void goal() {
        }
    }

    static final class InputIdConverter implements TypedGoalValueConverter<InputId> {
        @Override
        public Class<InputId> targetType() {
            return InputId.class;
        }

        @Override
        public InputId convert(String rawValue, GoalParameterBindingContext context) {
            return new InputId(rawValue);
        }
    }

    record InputId(String value) {
    }

    static final class BundleWorkflow {
        @ActionGraphAction(
                id = "bundle.finish",
                preconditions = BUNDLE_START,
                effects = BUNDLE_DONE,
                description = "Finish a bundle goal."
        )
        ActionResult finish(@BlackboardValue("ticketId") String ticketId) {
            return ActionResult.ok();
        }
    }

    static final class BundleWorkflowDriftedRisk {
        @ActionGraphAction(
                id = "bundle.finish",
                preconditions = BUNDLE_START,
                effects = BUNDLE_DONE,
                description = "Finish a bundle goal.",
                riskLevel = ActionRiskLevel.HIGH
        )
        ActionResult finish(@BlackboardValue("ticketId") String ticketId) {
            return ActionResult.ok();
        }
    }
}
