package com.actiongraph.samples.claimsprecheck.batch;

import com.actiongraph.action.ActionId;
import com.actiongraph.policy.HumanReviewDecision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ClaimsPrecheckBatchReviewCallbackJsonl {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ClaimsPrecheckBatchReviewCallbackJsonl() {
    }

    public static List<ClaimsPrecheckBatchReviewCallback> read(Path input) {
        Objects.requireNonNull(input, "input");
        try {
            List<ClaimsPrecheckBatchReviewCallback> callbacks = new ArrayList<>();
            int lineNumber = 0;
            for (String line : Files.readAllLines(input, StandardCharsets.UTF_8)) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                callbacks.add(parseLine(trimmed, input, lineNumber));
            }
            if (callbacks.isEmpty()) {
                throw new IllegalArgumentException("review callback file must contain at least one callback: " + input);
            }
            return List.copyOf(callbacks);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read claims precheck review callbacks from " + input, ex);
        }
    }

    private static ClaimsPrecheckBatchReviewCallback parseLine(String line, Path input, int lineNumber) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(line);
            return new ClaimsPrecheckBatchReviewCallback(
                    optionalString(root, "deliveryId"),
                    requiredString(root, "claimId"),
                    optionalString(root, "runId"),
                    new ActionId(requiredString(root, "actionId")),
                    optionalInt(root, "expectedStageIndex", "stageIndex"),
                    HumanReviewDecision.valueOf(requiredString(root, "decision")),
                    optionalString(root, "reviewer"),
                    optionalString(root, "comment"),
                    optionalLong(root, "decisionDelayMs", "decisionDelayMillis"),
                    optionalString(root, "token")
            );
        } catch (RuntimeException | IOException ex) {
            throw new IllegalArgumentException("Invalid review callback JSONL record at "
                    + input + ":" + lineNumber, ex);
        }
    }

    private static String requiredString(JsonNode root, String field) {
        String value = optionalString(root, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing required callback field: " + field);
        }
        return value;
    }

    private static String optionalString(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText();
    }

    private static int optionalInt(JsonNode root, String primaryField, String fallbackField) {
        JsonNode value = root.has(primaryField) ? root.path(primaryField) : root.path(fallbackField);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return 0;
        }
        return value.asInt();
    }

    private static long optionalLong(JsonNode root, String primaryField, String fallbackField) {
        JsonNode value = root.has(primaryField) ? root.path(primaryField) : root.path(fallbackField);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return 0;
        }
        return value.asLong();
    }
}
