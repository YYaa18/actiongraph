package com.actiongraph.trace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class TraceHasher {
    private TraceHasher() {
    }

    public static String hash(TraceEvent event) {
        Objects.requireNonNull(event, "event");
        return hash(
                event.runId(),
                event.seq(),
                event.at(),
                event.type(),
                event.actionId(),
                event.detail(),
                event.data(),
                event.prevHash()
        );
    }

    public static String hash(
            String runId,
            long seq,
            Instant at,
            TraceEventType type,
            String actionId,
            String detail,
            Map<String, String> data,
            String prevHash
    ) {
        String canonical = canonical(runId, seq, at, type, actionId, detail, data, prevHash);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    static String canonical(
            String runId,
            long seq,
            Instant at,
            TraceEventType type,
            String actionId,
            String detail,
            Map<String, String> data,
            String prevHash
    ) {
        String sortedData = (data == null ? Map.<String, String>of() : data).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
        return String.join("|",
                Objects.requireNonNull(runId, "runId"),
                Long.toString(seq),
                Objects.requireNonNull(at, "at").toString(),
                Objects.requireNonNull(type, "type").name(),
                actionId == null ? "" : actionId,
                detail == null ? "" : detail,
                sortedData,
                prevHash == null ? "" : prevHash
        );
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
