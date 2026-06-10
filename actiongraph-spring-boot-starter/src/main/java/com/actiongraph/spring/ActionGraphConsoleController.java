package com.actiongraph.spring;

import com.actiongraph.persistence.jdbc.JdbcTraceRunRepository;
import com.actiongraph.persistence.jdbc.TraceRunSummary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.console.path:/actiongraph/console}")
public final class ActionGraphConsoleController {
    private final JdbcTraceRunRepository runRepository;
    private final ActionGraphProperties.ConsoleProperties properties;

    public ActionGraphConsoleController(
            JdbcTraceRunRepository runRepository,
            ActionGraphProperties.ConsoleProperties properties
    ) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @GetMapping("/runs")
    public ConsoleRunsResponse recentRuns(
            @RequestHeader HttpHeaders headers,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        verifyToken(headers);
        int resolvedLimit = resolveLimit(limit);
        List<ConsoleRunSummaryResponse> runs = runRepository.findRecentRuns(resolvedLimit)
                .stream()
                .map(ConsoleRunSummaryResponse::from)
                .toList();
        return new ConsoleRunsResponse(resolvedLimit, runs.size(), runs);
    }

    @GetMapping("/runs/{runId}")
    public ConsoleRunSummaryResponse run(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId
    ) {
        verifyToken(headers);
        return runRepository.findRun(runId)
                .map(ConsoleRunSummaryResponse::from)
                .orElseThrow(() -> new ConsoleRunNotFoundException(runId));
    }

    private int resolveLimit(Integer limit) {
        int resolved = limit == null ? properties.getDefaultLimit() : limit;
        if (resolved <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (resolved > properties.getMaxLimit()) {
            throw new IllegalArgumentException("limit must not exceed " + properties.getMaxLimit());
        }
        return resolved;
    }

    private void verifyToken(HttpHeaders headers) {
        if (!properties.hasSharedSecret()) {
            return;
        }
        String actual = headers.getFirst(properties.getTokenHeader());
        if (!sameSecret(properties.getSharedSecret(), actual)) {
            throw new UnauthorizedConsoleException();
        }
    }

    private boolean sameSecret(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual == null
                ? new byte[0]
                : actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    @ExceptionHandler(UnauthorizedConsoleException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ConsoleErrorResponse handleUnauthorized(UnauthorizedConsoleException exception) {
        return new ConsoleErrorResponse("UNAUTHORIZED", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ConsoleErrorResponse handleBadRequest(IllegalArgumentException exception) {
        return new ConsoleErrorResponse("BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(ConsoleRunNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ConsoleErrorResponse handleNotFound(ConsoleRunNotFoundException exception) {
        return new ConsoleErrorResponse("NOT_FOUND", exception.getMessage());
    }

    public record ConsoleRunsResponse(
            int limit,
            int count,
            List<ConsoleRunSummaryResponse> runs
    ) {
    }

    public record ConsoleRunSummaryResponse(
            String runId,
            Instant firstEventAt,
            Instant lastEventAt,
            String status,
            int traceEventCount,
            boolean auditComplete,
            long firstBrokenSeq,
            String auditMessage
    ) {
        private static ConsoleRunSummaryResponse from(TraceRunSummary summary) {
            return new ConsoleRunSummaryResponse(
                    summary.runId(),
                    summary.firstEventAt(),
                    summary.lastEventAt(),
                    summary.status(),
                    summary.traceEventCount(),
                    summary.auditComplete(),
                    summary.firstBrokenSeq(),
                    summary.auditMessage()
            );
        }
    }

    public record ConsoleErrorResponse(
            String error,
            String message
    ) {
    }

    private static final class UnauthorizedConsoleException extends RuntimeException {
        private UnauthorizedConsoleException() {
            super("Console token is missing or invalid");
        }
    }

    private static final class ConsoleRunNotFoundException extends RuntimeException {
        private ConsoleRunNotFoundException(String runId) {
            super("Trace run not found: " + runId);
        }
    }
}
