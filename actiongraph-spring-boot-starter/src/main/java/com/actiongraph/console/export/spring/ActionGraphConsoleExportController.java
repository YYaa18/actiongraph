package com.actiongraph.console.export.spring;

import com.actiongraph.console.ConsoleRunNotFoundException;
import com.actiongraph.console.export.ActionGraphConsoleExportService;
import com.actiongraph.console.spring.ActionGraphConsoleProperties;
import com.actiongraph.controlplane.api.ControlPlaneErrorResponse;
import com.actiongraph.controlplane.auth.ForbiddenControlPlaneAccessException;
import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifier;
import com.actiongraph.spring.security.ActionGraphEndpointGroup;
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
import java.util.Objects;

import org.jspecify.annotations.Nullable;

@RestController
@RequestMapping("${actiongraph.console.path:/actiongraph/console}")
public final class ActionGraphConsoleExportController {
    private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private static final MediaType JSONL = MediaType.parseMediaType("application/x-ndjson;charset=UTF-8");
    private static final String UNAUTHORIZED_MESSAGE = "Console token is missing or invalid";

    private final ActionGraphConsoleExportService exportService;
    private final ActionGraphConsoleProperties properties;
    private final ActionGraphEndpointAccessVerifier accessVerifier;

    public ActionGraphConsoleExportController(
            ActionGraphConsoleExportService exportService,
            ActionGraphConsoleProperties properties,
            ActionGraphEndpointAccessVerifier accessVerifier
    ) {
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.accessVerifier = Objects.requireNonNull(accessVerifier, "accessVerifier");
    }

    @GetMapping(value = "/runs/export.csv", produces = "text/csv")
    public ResponseEntity<String> runsCsv(
            @RequestHeader HttpHeaders headers,
            @RequestParam(name = "limit", required = false) @Nullable Integer limit,
            @RequestParam(name = "offset", required = false) @Nullable Integer offset,
            @RequestParam(name = "status", required = false) @Nullable String status,
            @RequestParam(name = "auditComplete", required = false) @Nullable Boolean auditComplete
    ) {
        verifyAccess(headers);
        return download("actiongraph-runs.csv", TEXT_CSV,
                exportService.runsCsv(limit, offset, status, auditComplete));
    }

    @GetMapping(value = "/runs/{runId}/trace/export.csv", produces = "text/csv")
    public ResponseEntity<String> traceCsv(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId
    ) {
        verifyAccess(headers);
        return download("actiongraph-trace-" + safeFilename(runId) + ".csv", TEXT_CSV,
                exportService.traceCsv(runId));
    }

    @GetMapping(value = "/runs/{runId}/trace/export.jsonl", produces = "application/x-ndjson")
    public ResponseEntity<String> traceJsonl(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId
    ) {
        verifyAccess(headers);
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

    private void verifyAccess(HttpHeaders headers) {
        accessVerifier.verify(ActionGraphEndpointGroup.CONSOLE, properties, headers::getFirst, UNAUTHORIZED_MESSAGE);
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

    @ExceptionHandler(UnauthorizedControlPlaneAccessException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ControlPlaneErrorResponse handleUnauthorized(UnauthorizedControlPlaneAccessException exception) {
        return ControlPlaneErrorResponse.unauthorized(exception.getMessage());
    }

    @ExceptionHandler(ForbiddenControlPlaneAccessException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ControlPlaneErrorResponse handleForbidden(ForbiddenControlPlaneAccessException exception) {
        return ControlPlaneErrorResponse.forbidden(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ControlPlaneErrorResponse handleBadRequest(IllegalArgumentException exception) {
        return ControlPlaneErrorResponse.badRequest(exception.getMessage());
    }

    @ExceptionHandler(ConsoleRunNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ControlPlaneErrorResponse handleNotFound(ConsoleRunNotFoundException exception) {
        return ControlPlaneErrorResponse.notFound(exception.getMessage());
    }

}
