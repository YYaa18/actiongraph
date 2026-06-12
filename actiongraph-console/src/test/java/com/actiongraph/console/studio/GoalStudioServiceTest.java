package com.actiongraph.console.studio;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.fingerprint.ActionGraphFingerprints;
import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.annotation.GoalValueConverterResolver;
import com.actiongraph.interpretation.config.ConfiguredGoalDefinitionFactory;
import com.actiongraph.planning.Condition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GoalStudioServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void draftsValidGoalAndApprovesBundle() throws Exception {
        FakeLlmClient llm = new FakeLlmClient(validDraft());
        DefaultActionRegistry registry = registry();
        GoalStudioService studio = studio(llm, registry, 3);

        GoalStudioSessionResponse response = studio.createSession("Create a generated demo goal");

        assertThat(response.status()).isEqualTo(GoalStudioStatus.DRAFT_VALID);
        assertThat(response.previewPlan()).containsExactly("demo.load", "demo.finish");
        assertThat(response.riskProfile())
                .extracting(GoalStudioRiskItem::actionId)
                .containsExactly("demo.load", "demo.finish");
        assertThat(response.riskProfile().get(1).riskLevel()).isEqualTo(ActionRiskLevel.HIGH);
        assertThat(response.riskProfile().get(1).requiresHumanReview()).isTrue();
        assertThat(llm.requests()).singleElement().satisfies(request ->
                assertThat(request.systemPrompt())
                        .contains("demo.finish")
                        .contains("Finish with review")
                        .contains("requiresHumanReview=true"));

        GoalStudioSessionResponse approved = studio.approve(response.id(), "zhang.san");

        assertThat(approved.status()).isEqualTo(GoalStudioStatus.APPROVED);
        assertThat(approved.bundle()).contains("approved-by: \"zhang.san\"");
        assertThat(approved.bundle()).contains("action-graph-fingerprint: \""
                + ActionGraphFingerprints.actionGraph(registry.all()) + "\"");
        assertThat(Files.readString(Path.of(approved.bundlePath()))).isEqualTo(approved.bundle());
    }

    @Test
    void feedsValidationDiagnosticsBackIntoLlmForAutoRepair() {
        FakeLlmClient llm = new FakeLlmClient(invalidDraft(), validDraft());
        GoalStudioService studio = studio(llm, registry(), 3);

        GoalStudioSessionResponse response = studio.createSession("Repair this goal");

        assertThat(response.status()).isEqualTo(GoalStudioStatus.DRAFT_VALID);
        assertThat(response.repairAttempts()).isEqualTo(1);
        assertThat(llm.requests()).hasSize(2);
        assertThat(llm.requests().get(1).userPrompt())
                .contains("Validation diagnostics to fix exactly")
                .contains("missing condition(s) demo:MISSING");
    }

    @Test
    void stopsAfterMaxAutoRepairsAndReturnsDiagnostics() {
        FakeLlmClient llm = new FakeLlmClient(invalidDraft(), invalidDraft());
        GoalStudioService studio = studio(llm, registry(), 1);

        GoalStudioSessionResponse response = studio.createSession("Still invalid");

        assertThat(response.status()).isEqualTo(GoalStudioStatus.DRAFT_INVALID);
        assertThat(response.reachable()).isFalse();
        assertThat(response.diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic).contains("missing condition(s) demo:MISSING"));
        assertThat(llm.requests()).hasSize(2);
    }

    private GoalStudioService studio(FakeLlmClient llm, DefaultActionRegistry registry, int maxRepairs) {
        return new GoalStudioService(
                llm,
                new GoalCatalog(),
                registry,
                new ConfiguredGoalDefinitionFactory(GoalValueConverterResolver.reflection()),
                maxRepairs,
                tempDir,
                "test",
                Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private DefaultActionRegistry registry() {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        registry.register(new TestAction(
                "demo.load",
                "Load demo state",
                Set.of(Condition.of("demo:START")),
                Set.of(Condition.of("demo:LOADED")),
                ActionRiskLevel.LOW,
                false
        ));
        registry.register(new TestAction(
                "demo.finish",
                "Finish with review",
                Set.of(Condition.of("demo:LOADED")),
                Set.of(Condition.of("demo:DONE")),
                ActionRiskLevel.HIGH,
                true
        ));
        return registry;
    }

    private String validDraft() {
        return """
                {
                  "type": "demo.generated",
                  "description": "Generated demo goal",
                  "targetConditions": ["demo:DONE"],
                  "seedConditions": ["demo:START"],
                  "parameters": [],
                  "steps": ["demo.load", "demo.finish"]
                }
                """;
    }

    private String invalidDraft() {
        return """
                {
                  "type": "demo.generated",
                  "description": "Broken generated demo goal",
                  "targetConditions": ["demo:MISSING"],
                  "seedConditions": ["demo:START"],
                  "parameters": []
                }
                """;
    }

    private static final class FakeLlmClient implements GoalStudioLanguageModel {
        private final List<String> responses;
        private final List<Request> requests = new ArrayList<>();
        private int index;

        private FakeLlmClient(String... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public String complete(String systemPrompt, String userPrompt, int maxTokens) {
            requests.add(new Request(systemPrompt, userPrompt, maxTokens));
            String response = responses.get(Math.min(index, responses.size() - 1));
            index++;
            return response;
        }

        List<Request> requests() {
            return requests;
        }
    }

    private record Request(String systemPrompt, String userPrompt, int maxTokens) {
    }

    private record TestAction(
            String rawId,
            String description,
            Set<Condition> preconditions,
            Set<Condition> effects,
            ActionRiskLevel riskLevel,
            boolean requiresHumanReview
    ) implements Action {
        @Override
        public ActionId id() {
            return new ActionId(rawId);
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
        public int cost() {
            return 1;
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            return ActionResult.ok();
        }
    }
}
