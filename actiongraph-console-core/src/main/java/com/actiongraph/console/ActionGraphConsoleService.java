package com.actiongraph.console;

import com.actiongraph.trace.TraceEvent;

import java.util.List;
import java.util.Objects;

public final class ActionGraphConsoleService {
    private final ConsoleRunRepository runRepository;
    private final ConsoleOptions options;

    public ActionGraphConsoleService(ConsoleRunRepository runRepository, ConsoleOptions options) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository");
        this.options = Objects.requireNonNull(options, "options");
    }

    public ConsoleRunsResponse recentRuns(
            Integer limit,
            Integer offset,
            String status,
            Boolean auditComplete
    ) {
        int resolvedLimit = resolveLimit(limit);
        int resolvedOffset = resolveOffset(offset);
        ConsoleRunQuery query = new ConsoleRunQuery(resolvedLimit, resolvedOffset, status, auditComplete);
        ConsoleRunPage page = runRepository.findRuns(query);
        List<ConsoleRunSummaryResponse> runs = page.runs()
                .stream()
                .map(ConsoleRunSummaryResponse::from)
                .toList();
        return new ConsoleRunsResponse(
                page.limit(),
                page.offset(),
                page.total(),
                runs.size(),
                page.hasMore(),
                query.status(),
                query.auditComplete(),
                runs
        );
    }

    public ConsoleRunSummaryResponse run(String runId) {
        return runRepository.findRun(runId)
                .map(ConsoleRunSummaryResponse::from)
                .orElseThrow(() -> new ConsoleRunNotFoundException(runId));
    }

    public ConsoleTraceResponse trace(String runId) {
        List<TraceEvent> events = runRepository.findTraceEvents(runId);
        if (events.isEmpty()) {
            throw new ConsoleRunNotFoundException(runId);
        }
        List<ConsoleTraceEventResponse> responses = events.stream()
                .map(ConsoleTraceEventResponse::from)
                .toList();
        return new ConsoleTraceResponse(runId, responses.size(), responses);
    }

    public String renderPage(String template) {
        return ConsolePageRenderer.render(template, options);
    }

    private int resolveLimit(Integer limit) {
        int resolved = limit == null ? options.defaultLimit() : limit;
        if (resolved <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (resolved > options.maxLimit()) {
            throw new IllegalArgumentException("limit must not exceed " + options.maxLimit());
        }
        return resolved;
    }

    private int resolveOffset(Integer offset) {
        int resolved = offset == null ? 0 : offset;
        if (resolved < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        return resolved;
    }
}
