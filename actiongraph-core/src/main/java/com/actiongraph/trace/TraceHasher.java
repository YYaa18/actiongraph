package com.actiongraph.trace;

import com.actiongraph.exception.ActionGraphIntegrationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Deterministic SHA-256 hasher for trace-chain events.
 *
 * <p>The hasher canonicalizes the masked event payload by sorting data keys and
 * normalizing optional text to empty strings. It is stateless and safe to call
 * concurrently.
 */
public final class TraceHasher {
    private TraceHasher() {
    }

    /**
     * Computes the hash for an event using its stored payload and previous hash.
     *
     * @param event event to hash; never {@code null}
     * @return lowercase hexadecimal SHA-256 hash
     */
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

    /**
     * Computes the hash for a trace payload.
     *
     * @return lowercase hexadecimal SHA-256 hash
     */
    public static String hash(
            String runId,
            long seq,
            Instant at,
            TraceEventType type,
            @Nullable String actionId,
            @Nullable String detail,
            @Nullable Map<String, String> data,
            @Nullable String prevHash
    ) {
        String canonical = canonical(runId, seq, at, type, actionId, detail, data, prevHash);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new ActionGraphIntegrationException("SHA-256 is not available", ex);
        }
    }

    static String canonical(
            String runId,
            long seq,
            Instant at,
            TraceEventType type,
            @Nullable String actionId,
            @Nullable String detail,
            @Nullable Map<String, String> data,
            @Nullable String prevHash
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
