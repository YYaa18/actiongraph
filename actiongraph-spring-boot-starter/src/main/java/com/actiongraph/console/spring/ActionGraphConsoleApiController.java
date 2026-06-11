package com.actiongraph.console.spring;

import com.actiongraph.console.ActionGraphConsoleService;
import com.actiongraph.console.ConsoleRunNotFoundException;
import com.actiongraph.console.ConsoleRunSummaryResponse;
import com.actiongraph.console.ConsoleRunsResponse;
import com.actiongraph.console.ConsoleTraceResponse;
import com.actiongraph.controlplane.api.ControlPlaneErrorResponse;
import com.actiongraph.controlplane.auth.ControlPlaneTokenVerifier;
import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;
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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

@RestController
@RequestMapping("${actiongraph.console.path:/actiongraph/console}")
public final class ActionGraphConsoleApiController {
    private static final ControlPlaneTokenVerifier TOKEN_VERIFIER = new ControlPlaneTokenVerifier();
    private static final String UNAUTHORIZED_MESSAGE = "Console token is missing or invalid";

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
            @RequestParam(name = "limit", required = false) @Nullable Integer limit,
            @RequestParam(name = "offset", required = false) @Nullable Integer offset,
            @RequestParam(name = "status", required = false) @Nullable String status,
            @RequestParam(name = "auditComplete", required = false) @Nullable Boolean auditComplete
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
        TOKEN_VERIFIER.verify(properties, headers::getFirst, UNAUTHORIZED_MESSAGE);
    }

    @ExceptionHandler(UnauthorizedControlPlaneAccessException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ControlPlaneErrorResponse handleUnauthorized(UnauthorizedControlPlaneAccessException exception) {
        return ControlPlaneErrorResponse.unauthorized(exception.getMessage());
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
