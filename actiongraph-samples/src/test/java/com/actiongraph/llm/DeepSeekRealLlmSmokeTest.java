package com.actiongraph.llm;

import com.actiongraph.samples.renewal.interpretation.RenewalGoalInterpreterFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekRealLlmSmokeTest {
    @Test
    void realDeepSeekInterpretsChineseRenewalRequest() {
        var interpreter = RenewalGoalInterpreterFactory.createWithOptionalLlm(DeepSeekChatClient.fromEnvironment());

        var interpretation = interpreter.interpret("帮客户 C001 的合同弄个续约报价");

        assertThat(interpretation.isReady()).isTrue();
        assertThat(interpretation.parameters().get("customerId")).contains("C001");
        assertThat(interpretation.goalType().value()).isEqualTo("prepareRenewalQuote");
    }
}
