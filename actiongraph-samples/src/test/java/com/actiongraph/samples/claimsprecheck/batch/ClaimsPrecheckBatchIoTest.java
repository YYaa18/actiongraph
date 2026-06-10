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

        new ClaimsPrecheckBatchReportWriter().write(tempDir, metrics);

        Path markdown = tempDir.resolve(ClaimsPrecheckBatchReportWriter.MARKDOWN_REPORT);
        Path csv = tempDir.resolve(ClaimsPrecheckBatchReportWriter.CSV_RESULTS);
        assertThat(Files.readString(markdown))
                .contains("Total Runs: 5")
                .contains("Intercept Rate: 60.00%")
                .contains("Audit Completeness Rate: 100.00%");
        assertThat(Files.readString(csv))
                .contains("claimId,status,businessIntercepted,auditComplete")
                .contains("CLM103,DENIED_BY_POLICY,true,true");
    }

    private Path bundledCsv() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                getClass().getResource("/claims-precheck-cases.csv")
        ).toURI());
    }
}
