package com.actiongraph.samples.renewal.interpretation;

import com.actiongraph.llm.DeepSeekChatClient;
import com.actiongraph.llm.evals.GoldenSetAssertions;
import com.actiongraph.llm.evals.Thresholds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URISyntaxException;
import java.nio.file.Path;

class RenewalGoldenSetEvalTest {
    @Test
    void ruleBasedRenewalInterpreterMeetsGoldenSetExactly() throws Exception {
        GoldenSetAssertions.assertMeets(
                new RuleBasedRenewalGoalInterpreter(),
                goldenSet(),
                Thresholds.exact()
        );
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
    void deepSeekRenewalInterpreterMeetsGoldenSetThresholds() throws Exception {
        GoldenSetAssertions.assertMeets(
                RenewalGoalInterpreterFactory.createWithOptionalLlm(DeepSeekChatClient.fromEnvironment()),
                goldenSet(),
                Thresholds.of(0.9d, 0.85d, 0.9d)
        );
    }

    private static Path goldenSet() throws URISyntaxException {
        return Path.of(RenewalGoldenSetEvalTest.class
                .getResource("/golden/renewal-golden.jsonl")
                .toURI());
    }
}
