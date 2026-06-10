package com.actiongraph.governance;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegexMaskingPolicyTest {
    @Test
    void masksFinancialIdentifiersInText() {
        RegexMaskingPolicy policy = RegexMaskingPolicy.financialDefaults();

        String masked = policy.maskText(
                "id=110101199001011234 card=6228481234561234 phone=13812345678 email=zhangsan@bank.com"
        );

        assertThat(masked)
                .contains("110101********1234")
                .contains("622848******1234")
                .contains("138****5678")
                .contains("z***@bank.com")
                .doesNotContain("110101199001011234")
                .doesNotContain("6228481234561234")
                .doesNotContain("13812345678")
                .doesNotContain("zhangsan@bank.com");
    }

    @Test
    void blockedKeysMaskWholeValueBeforeShapeMatching() {
        RegexMaskingPolicy policy = RegexMaskingPolicy.builder()
                .addFinancialDefaults()
                .addBlockedKey("customerName")
                .build();

        Map<String, String> masked = policy.maskData(Map.of(
                "customerName", "张三",
                "mobile", "13812345678"
        ));

        assertThat(masked)
                .containsEntry("customerName", "***")
                .containsEntry("mobile", "138****5678");
    }
}
