package com.actiongraph.samples.claimsprecheck.batch;

import com.actiongraph.runtime.RunStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimsPrecheckBatchRunnerTest {
    @Test
    void defaultBatchProducesF1OperationalMetrics() {
        ClaimsPrecheckBatchMetrics metrics = new ClaimsPrecheckBatchRunner()
                .run(ClaimsPrecheckBatchRunner.defaultCases());

        assertThat(metrics.totalRuns()).isEqualTo(5);
        assertThat(metrics.completedRuns()).isEqualTo(1);
        assertThat(metrics.interceptedRuns()).isEqualTo(3);
        assertThat(metrics.failedRuns()).isEqualTo(1);
        assertThat(metrics.auditCompleteRuns()).isEqualTo(5);
        assertThat(metrics.interceptRate()).isEqualTo(60.0);
        assertThat(metrics.auditCompletenessRate()).isEqualTo(100.0);
        assertThat(metrics.averageRuntimeMillis()).isGreaterThanOrEqualTo(0.0);

        assertThat(metrics.caseResults())
                .extracting(ClaimsPrecheckCaseResult::claimId, ClaimsPrecheckCaseResult::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("CLM100", RunStatus.COMPLETED),
                        org.assertj.core.groups.Tuple.tuple("CLM101", RunStatus.HALTED_UNREACHABLE),
                        org.assertj.core.groups.Tuple.tuple("CLM102", RunStatus.HALTED_UNREACHABLE),
                        org.assertj.core.groups.Tuple.tuple("CLM103", RunStatus.DENIED_BY_POLICY),
                        org.assertj.core.groups.Tuple.tuple("CLM104", RunStatus.FAILED_COMPENSATED)
                );
        assertThat(metrics.caseResults())
                .allSatisfy(result -> {
                    assertThat(result.auditComplete()).isTrue();
                    assertThat(result.traceEventCount()).isPositive();
                });
    }
}
