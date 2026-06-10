package com.actiongraph.console.export.spring;

import com.actiongraph.console.ConsoleErrorResponse;
import com.actiongraph.console.ConsoleRunNotFoundException;
import com.actiongraph.console.export.ActionGraphConsoleExportService;
import com.actiongraph.console.spring.ActionGraphConsoleProperties;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.console.path:/actiongraph/console}")
public final class ActionGraphConsoleExportController {
    private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private static final MediaType JSONL = MediaType.parseMediaType("application/x-ndjson;charset=UTF-8");

    private final ActionGraphConsoleExportService exportService;
    private final ActionGraphConsoleProperties properties;

    public ActionGraphConsoleExportController(
            ActionGraphConsoleExportService exportService,
            ActionGraphConsoleProperties properties
    ) {
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @GetMapping(value = "/runs/export.csv", produces = "text/csv")
    public ResponseEntity<String> runsCsv(
            @RequestHeader HttpHeaders headers,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "auditComplete", required = false) Boolean auditComplete
    ) {
        verifyToken(headers);
        return download("actiongraph-runs.csv", TEXT_CSV,
                exportService.runsCsv(limit, offset, status, auditComplete));
    }

    @GetMapping(value = "/runs/{runId}/trace/export.csv", produces = "text/csv")
    public ResponseEntity<String> traceCsv(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId
    ) {
        verifyToken(headers);
        return download("actiongraph-trace-" + safeFilename(runId) + ".csv", TEXT_CSV,
                exportService.traceCsv(runId));
    }

    @GetMapping(value = "/runs/{runId}/trace/export.jsonl", produces = "application/x-ndjson")
    public ResponseEntity<String> traceJsonl(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId
    ) {
        verifyToken(headers);
        return download("actiongraph-trace-" + safeFilename(runId) + ".jsonl", JSONL,
                exportService.traceJsonl(runId));
    }

    private ResponseEntity<String> download(String filename, MediaType contentType, String body) {
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(body);
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

    private String safeFilename(String value) {
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '-'
                    || ch == '_'
                    || ch == '.') {
                safe.append(ch);
            } else {
                safe.append('_');
            }
        }
        return safe.isEmpty() ? "run" : safe.toString();
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

    private static final class UnauthorizedConsoleException extends RuntimeException {
        private UnauthorizedConsoleException() {
            super("Console token is missing or invalid");
        }
    }
}
