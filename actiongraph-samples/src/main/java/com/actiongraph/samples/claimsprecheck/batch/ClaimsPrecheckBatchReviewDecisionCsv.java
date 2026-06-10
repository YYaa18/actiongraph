package com.actiongraph.samples.claimsprecheck.batch;

import com.actiongraph.action.ActionId;
import com.actiongraph.policy.HumanReviewDecision;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ClaimsPrecheckBatchReviewDecisionCsv {
    private static final CSVFormat INPUT_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .get();

    private ClaimsPrecheckBatchReviewDecisionCsv() {
    }

    public static List<ClaimsPrecheckBatchReviewDecision> read(Path input) {
        Objects.requireNonNull(input, "input");
        try (CSVParser parser = CSVParser.parse(input, StandardCharsets.UTF_8, INPUT_FORMAT)) {
            List<ClaimsPrecheckBatchReviewDecision> decisions = new ArrayList<>();
            parser.forEach(record -> decisions.add(new ClaimsPrecheckBatchReviewDecision(
                    record.get("claimId"),
                    new ActionId(record.get("actionId")),
                    parseInt(record.get("stageIndex")),
                    HumanReviewDecision.valueOf(record.get("decision")),
                    record.get("reviewer"),
                    record.get("comment"),
                    parseLong(record.get("decisionDelayMs"))
            )));
            return List.copyOf(decisions);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read claims precheck review decisions from " + input, ex);
        }
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Long.parseLong(value.trim());
    }
}
