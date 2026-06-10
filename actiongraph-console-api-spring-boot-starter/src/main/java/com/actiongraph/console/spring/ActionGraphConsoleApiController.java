package com.actiongraph.console.spring;

import com.actiongraph.console.ActionGraphConsoleService;
import com.actiongraph.console.ConsoleErrorResponse;
import com.actiongraph.console.ConsoleRunNotFoundException;
import com.actiongraph.console.ConsoleRunSummaryResponse;
import com.actiongraph.console.ConsoleRunsResponse;
import com.actiongraph.console.ConsoleTraceResponse;
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
import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.console.path:/actiongraph/console}")
public final class ActionGraphConsoleApiController {
    private final ActionGraphConsoleService consoleService;
    private final ActionGraphConsoleProperties properties;

    public ActionGraphConsoleApiController(
            ActionGraphConsoleService consoleService,
            ActionGraphConsoleProperties properties
    ) {
        this.consoleService = Objects.requireNonNull(consoleService, "consoleService");
        this.properties = Objects.requireNonNull(properties, "properties");
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
        return consoleService.recentRuns(limit, offset, status, auditComplete);
    }

    @GetMapping("/runs/{runId}")
    public ConsoleRunSummaryResponse run(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId
    ) {
        verifyToken(headers);
        return consoleService.run(runId);
    }

    @GetMapping("/runs/{runId}/trace")
    public ConsoleTraceResponse trace(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId
    ) {
        verifyToken(headers);
        return consoleService.trace(runId);
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

    private static final class UnauthorizedConsoleException extends RuntimeException {
        private UnauthorizedConsoleException() {
            super("Console token is missing or invalid");
        }
    }
}
