package com.actiongraph.console.spring;

import com.actiongraph.persistence.jdbc.JdbcTraceRunRepository;
import com.actiongraph.persistence.jdbc.TraceRunPage;
import com.actiongraph.persistence.jdbc.TraceRunQuery;
import com.actiongraph.persistence.jdbc.TraceRunSummary;
import com.actiongraph.trace.TraceEvent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.console.path:/actiongraph/console}")
public final class ActionGraphConsoleController {
    private static final String CONSOLE_PAGE_RESOURCE = "/actiongraph/console/index.html";

    private final JdbcTraceRunRepository runRepository;
    private final ActionGraphConsoleProperties properties;
    private final String consolePage;

    public ActionGraphConsoleController(
            JdbcTraceRunRepository runRepository,
            ActionGraphConsoleProperties properties
    ) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.consolePage = renderConsolePage(loadConsolePage(), properties);
    }

    @GetMapping(value = {"", "/"}, produces = MediaType.TEXT_HTML_VALUE)
    public String page() {
        return consolePage;
    }

    @GetMapping("/runs")
    public ConsoleRunsResponse recentRuns(
            @RequestHeader HttpHeaders headers,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "auditComplete", required = false) Boolean auditComplete
    ) {
        verifyToken(headers);
        int resolvedLimit = resolveLimit(limit);
        int resolvedOffset = resolveOffset(offset);
        TraceRunQuery query = new TraceRunQuery(resolvedLimit, resolvedOffset, status, auditComplete);
        TraceRunPage page = runRepository.findRuns(query);
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

    @GetMapping("/runs/{runId}/trace")
    public ConsoleTraceResponse trace(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId
    ) {
        verifyToken(headers);
        List<TraceEvent> events = runRepository.findTraceEvents(runId);
        if (events.isEmpty()) {
            throw new ConsoleRunNotFoundException(runId);
        }
        List<ConsoleTraceEventResponse> responses = events.stream()
                .map(ConsoleTraceEventResponse::from)
                .toList();
        return new ConsoleTraceResponse(runId, responses.size(), responses);
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

    private int resolveOffset(Integer offset) {
        int resolved = offset == null ? 0 : offset;
        if (resolved < 0) {
            throw new IllegalArgumentException("offset must not be negative");
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

    private String loadConsolePage() {
        try (InputStream input = ActionGraphConsoleController.class.getResourceAsStream(CONSOLE_PAGE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Console page resource not found: " + CONSOLE_PAGE_RESOURCE);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read console page resource", ex);
        }
    }

    private String renderConsolePage(String template, ActionGraphConsoleProperties consoleProperties) {
        return template
                .replace("__ACTIONGRAPH_CONSOLE_TOKEN_HEADER__", jsString(consoleProperties.getTokenHeader()))
                .replace("__ACTIONGRAPH_CONSOLE_DEFAULT_LIMIT__", Integer.toString(consoleProperties.getDefaultLimit()))
                .replace("__ACTIONGRAPH_CONSOLE_MAX_LIMIT__", Integer.toString(consoleProperties.getMaxLimit()));
    }

    private String jsString(String value) {
        StringBuilder escaped = new StringBuilder("'");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '\'' -> escaped.append("\\'");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '<' -> escaped.append("\\u003c");
                case '>' -> escaped.append("\\u003e");
                case '&' -> escaped.append("\\u0026");
                default -> escaped.append(ch);
            }
        }
        return escaped.append("'").toString();
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
            int offset,
            long total,
            int count,
            boolean hasMore,
            String status,
            Boolean auditComplete,
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

    public record ConsoleTraceResponse(
            String runId,
            int count,
            List<ConsoleTraceEventResponse> events
    ) {
    }

    public record ConsoleTraceEventResponse(
            long seq,
            Instant at,
            String type,
            String actionId,
            String detail,
            Map<String, String> data,
            String prevHash,
            String hash
    ) {
        private static ConsoleTraceEventResponse from(TraceEvent event) {
            return new ConsoleTraceEventResponse(
                    event.seq(),
                    event.at(),
                    event.type().name(),
                    event.actionId(),
                    event.detail(),
                    event.data(),
                    event.prevHash(),
                    event.hash()
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
