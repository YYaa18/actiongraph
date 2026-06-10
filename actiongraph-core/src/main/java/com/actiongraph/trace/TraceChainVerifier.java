package com.actiongraph.trace;

import java.util.List;
import java.util.Objects;

public final class TraceChainVerifier {
    public ChainVerification verify(List<TraceEvent> events) {
        Objects.requireNonNull(events, "events");
        String previousHash = "";
        long previousSeq = 0;
        String runId = null;

        for (TraceEvent event : events) {
            if (runId == null) {
                runId = event.runId();
            } else if (!runId.equals(event.runId())) {
                return invalid(event.seq(), "Trace events must belong to the same run");
            }
            if (event.seq() <= previousSeq) {
                return invalid(event.seq(), "Trace events must be ordered by increasing seq");
            }
            if (event.hash().isBlank()) {
                return invalid(event.seq(), "Trace event has no hash; it may be pre-F0 data");
            }
            if (!event.prevHash().equals(previousHash)) {
                return invalid(event.seq(), "Trace event prevHash does not link to previous event");
            }
            String expected = TraceHasher.hash(event);
            if (!event.hash().equals(expected)) {
                return invalid(event.seq(), "Trace event hash does not match its stored payload");
            }
            previousSeq = event.seq();
            previousHash = event.hash();
        }

        return new ChainVerification(true, 0, "Trace chain is valid");
    }

    private static ChainVerification invalid(long firstBrokenSeq, String message) {
        return new ChainVerification(false, firstBrokenSeq, message);
    }

    public record ChainVerification(boolean valid, long firstBrokenSeq, String message) {
    }
}
