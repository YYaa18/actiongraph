package com.actiongraph.samples.claimsprecheck.batch;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ClaimsPrecheckBatchCsv {
    private static final CSVFormat INPUT_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .get();

    private ClaimsPrecheckBatchCsv() {
    }

    public static List<ClaimsPrecheckBatchCase> readCases(Path input) {
        Objects.requireNonNull(input, "input");
        try (CSVParser parser = CSVParser.parse(input, StandardCharsets.UTF_8, INPUT_FORMAT)) {
            List<ClaimsPrecheckBatchCase> cases = new ArrayList<>();
            parser.forEach(record -> cases.add(new ClaimsPrecheckBatchCase(
                    record.get("claimId"),
                    new BigDecimal(record.get("claimedAmount")),
                    parseBoolean(record.get("missingInvoice")),
                    parseBoolean(record.get("closed")),
                    parseBoolean(record.get("approvalFails")),
                    parseBoolean(record.get("expectedIntercept"))
            )));
            return List.copyOf(cases);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read claims precheck cases from " + input, ex);
        }
    }

    public static void writeResults(Path output, ClaimsPrecheckBatchMetrics metrics) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(metrics, "metrics");
        createParent(output);
        CSVFormat outputFormat = CSVFormat.DEFAULT.builder()
                .setHeader("claimId", "status", "businessIntercepted", "auditComplete",
                        "elapsedMs", "businessActionMs", "frameworkMs", "reviewWaitMs",
                        "executedActionCount", "traceEventCount")
                .get();
        try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(output, StandardCharsets.UTF_8), outputFormat)) {
            for (ClaimsPrecheckCaseResult result : metrics.caseResults()) {
                printer.printRecord(
                        result.claimId(),
                        result.status().name(),
                        result.businessIntercepted(),
                        result.auditComplete(),
                        String.format(Locale.ROOT, "%.3f", result.elapsedMillis()),
                        String.format(Locale.ROOT, "%.3f", result.businessActionMillis()),
                        String.format(Locale.ROOT, "%.3f", result.frameworkMillis()),
                        String.format(Locale.ROOT, "%.3f", result.reviewWaitMillis()),
                        result.executedActionCount(),
                        result.traceEventCount()
                );
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot write claims precheck results to " + output, ex);
        }
    }

    private static boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static void createParent(Path output) {
        Path parent = output.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create output directory " + parent, ex);
        }
    }
}
