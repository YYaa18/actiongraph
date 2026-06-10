package com.actiongraph.trace;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceChainVerifierTest {
    @Test
    void verifiesValidTraceChain() {
        TraceEvent first = hashed("RUN-CHAIN", 1, "", "started", Map.of("b", "2", "a", "1"));
        TraceEvent second = hashed("RUN-CHAIN", 2, first.hash(), "ended", Map.of("status", "COMPLETED"));

        var result = new TraceChainVerifier().verify(List.of(first, second));

        assertThat(result.valid()).isTrue();
        assertThat(result.firstBrokenSeq()).isZero();
    }

    @Test
    void detectsTamperedPayloadAtChangedEvent() {
        TraceEvent first = hashed("RUN-CHAIN", 1, "", "started", Map.of());
        TraceEvent second = hashed("RUN-CHAIN", 2, first.hash(), "ended", Map.of("status", "COMPLETED"));
        TraceEvent tampered = new TraceEvent(
                second.runId(),
                second.seq(),
                second.at(),
                second.type(),
                second.actionId(),
                "tampered",
                second.data(),
                second.prevHash(),
                second.hash()
        );

        var result = new TraceChainVerifier().verify(List.of(first, tampered));

        assertThat(result.valid()).isFalse();
        assertThat(result.firstBrokenSeq()).isEqualTo(2);
        assertThat(result.message()).contains("hash");
    }

    @Test
    void treatsMissingHashAsPreF0Data() {
        TraceEvent oldEvent = new TraceEvent(
                "RUN-OLD",
                1,
                Instant.parse("2026-01-01T00:00:01Z"),
                TraceEventType.RUN_STARTED,
                null,
                "old",
                Map.of()
        );

        var result = new TraceChainVerifier().verify(List.of(oldEvent));

        assertThat(result.valid()).isFalse();
        assertThat(result.firstBrokenSeq()).isEqualTo(1);
        assertThat(result.message()).contains("pre-F0");
    }

    private static TraceEvent hashed(
            String runId,
            long seq,
            String prevHash,
            String detail,
            Map<String, String> data
    ) {
        Instant at = Instant.parse("2026-01-01T00:00:0" + seq + "Z");
        String hash = TraceHasher.hash(runId, seq, at, TraceEventType.RUN_STARTED, null, detail, data, prevHash);
        return new TraceEvent(runId, seq, at, TraceEventType.RUN_STARTED, null, detail, data, prevHash, hash);
    }
}
