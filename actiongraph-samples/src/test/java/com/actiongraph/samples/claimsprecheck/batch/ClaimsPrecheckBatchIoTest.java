package com.actiongraph.samples.claimsprecheck.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimsPrecheckBatchIoTest {
    @TempDir
    Path tempDir;

    @Test
    void readsCsvCasesAndWritesMarkdownAndCsvReports() throws Exception {
        Path input = bundledCsv();
        var cases = ClaimsPrecheckBatchCsv.readCases(input);
        ClaimsPrecheckBatchMetrics metrics = new ClaimsPrecheckBatchRunner().run(cases);
        ClaimsPrecheckBatchReportMetadata metadata = new ClaimsPrecheckBatchReportMetadata(
                "BATCH-20260610",
                input.toString(),
                "test",
                ClaimsPrecheckBatchRunner.defaultLimitRules()
        );

        new ClaimsPrecheckBatchReportWriter().write(tempDir, metrics, metadata);

        assertThat(metrics.averageBusinessActionMillis()).isGreaterThanOrEqualTo(0.0);
        assertThat(metrics.averageFrameworkMillis()).isGreaterThanOrEqualTo(0.0);
        assertThat(metrics.averageReviewWaitMillis()).isGreaterThanOrEqualTo(0.0);
        assertThat(metrics.caseResults()).allSatisfy(result -> assertThat(
                result.businessActionNanos() + result.frameworkNanos() + result.reviewWaitNanos()
        ).isLessThanOrEqualTo(result.elapsedNanos()));

        Path markdown = tempDir.resolve(ClaimsPrecheckBatchReportWriter.MARKDOWN_REPORT);
        Path csv = tempDir.resolve(ClaimsPrecheckBatchReportWriter.CSV_RESULTS);
        assertThat(Files.readString(markdown))
                .contains("Total Runs: 5")
                .contains("Batch ID: BATCH-20260610")
                .contains("Environment: test")
                .contains("Sample Source: " + input)
                .contains("claim.approval.request")
                .contains("1000000")
                .contains("Intercept Rate: 60.00%")
                .contains("Average Business Action Ms")
                .contains("Average Framework Ms")
                .contains("Average Review Wait Ms")
                .contains("Business Action Ms")
                .contains("Audit Completeness Rate: 100.00%");
        assertThat(Files.readString(csv))
                .contains("claimId,status,businessIntercepted,auditComplete,elapsedMs,businessActionMs,frameworkMs,reviewWaitMs")
                .contains("CLM103,DENIED_BY_POLICY,true,true");
    }

    private Path bundledCsv() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                getClass().getResource("/claims-precheck-cases.csv")
        ).toURI());
    }
}
