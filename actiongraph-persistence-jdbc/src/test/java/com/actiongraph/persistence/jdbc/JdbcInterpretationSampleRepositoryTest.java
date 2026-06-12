package com.actiongraph.persistence.jdbc;

import com.actiongraph.interpretation.sampling.InterpretationSample;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcInterpretationSampleRepositoryTest {
    @Test
    void savesFindsLabelsAndAttachesRunId() {
        JdbcInterpretationSampleRepository repository =
                new JdbcInterpretationSampleRepository(JdbcTestDataSources.h2());
        InterpretationSample sample = new InterpretationSample(
                "SAMPLE-1",
                Instant.parse("2026-06-12T00:00:00Z"),
                "帮客户 138****5678 做续约报价",
                "clarification",
                "prepareRenewalQuote",
                Set.of("customerId"),
                true,
                false,
                null,
                false
        );

        repository.save(sample);
        repository.attachRunId("SAMPLE-1", "RUN-1");
        repository.markLabeled("SAMPLE-1");

        assertThat(repository.findById("SAMPLE-1"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.maskedInput()).doesNotContain("13812345678");
                    assertThat(saved.missingFields()).containsExactly("customerId");
                    assertThat(saved.runId()).isEqualTo("RUN-1");
                    assertThat(saved.labeled()).isTrue();
                    assertThat(saved.fallbackUsed()).isTrue();
                });
        assertThat(repository.findRecent(10))
                .extracting(InterpretationSample::id)
                .containsExactly("SAMPLE-1");
    }

    @Test
    void validatesTableName() {
        assertThatThrownBy(() -> new JdbcInterpretationSampleRepository(
                JdbcTestDataSources.h2(),
                "sample;drop table x"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SQL identifier");
    }
}
