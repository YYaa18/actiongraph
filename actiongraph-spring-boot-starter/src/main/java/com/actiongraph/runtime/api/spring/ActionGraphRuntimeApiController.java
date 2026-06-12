package com.actiongraph.runtime.api.spring;

import com.actiongraph.controlplane.api.ControlPlaneErrorResponse;
import com.actiongraph.controlplane.auth.ForbiddenControlPlaneAccessException;
import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.runtime.SuspendedRunNotClaimableException;
import com.actiongraph.runtime.api.ActionGraphRuntimeOperations;
import com.actiongraph.runtime.api.RuntimeInterpretationResponse;
import com.actiongraph.runtime.api.RuntimeRunResponse;
import com.actiongraph.runtime.api.RuntimeStartResponse;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifier;
import com.actiongraph.spring.security.ActionGraphEndpointGroup;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;

import org.jspecify.annotations.Nullable;

@RestController
@RequestMapping("${actiongraph.runtime.api.path:/actiongraph/runtime}")
public final class ActionGraphRuntimeApiController {
    private static final String UNAUTHORIZED_MESSAGE = "Runtime API token is missing or invalid";

    private final ActionGraphRuntimeOperations apiService;
    private final ActionGraphRuntimeApiProperties properties;
    private final ActionGraphEndpointAccessVerifier accessVerifier;

    public ActionGraphRuntimeApiController(
            ActionGraphRuntimeOperations apiService,
            ActionGraphRuntimeApiProperties properties,
            ActionGraphEndpointAccessVerifier accessVerifier
    ) {
        this.apiService = Objects.requireNonNull(apiService, "apiService");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.accessVerifier = Objects.requireNonNull(accessVerifier, "accessVerifier");
    }

    @PostMapping("/interpret")
    public RuntimeInterpretationResponse interpret(
            @RequestHeader HttpHeaders headers,
            @RequestBody RuntimeGoalRequest request
    ) {
        verifyAccess(headers);
        return apiService.interpret(request.input(), request.knownParametersOrEmpty());
    }

    @PostMapping("/runs")
    public RuntimeStartResponse start(
            @RequestHeader HttpHeaders headers,
            @RequestBody RuntimeGoalRequest request
    ) {
        RunPrincipal principal = verifyAccess(headers);
        return apiService.start(request.input(), request.knownParametersOrEmpty(), traceMetadata(headers), principal);
    }

    @PostMapping("/runs/{runId}/resume")
    public RuntimeRunResponse resume(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId
    ) {
        RunPrincipal actedBy = verifyAccess(headers);
        return apiService.resume(runId, traceMetadata(headers), actedBy);
    }

    private RunPrincipal verifyAccess(HttpHeaders headers) {
        return accessVerifier.verify(
                ActionGraphEndpointGroup.RUNTIME_API,
                properties,
                headers::getFirst,
                UNAUTHORIZED_MESSAGE
        );
    }

    private Map<String, String> traceMetadata(HttpHeaders headers) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String headerName : properties.getTraceHeaders()) {
            if (isTokenHeader(headerName)) {
                continue;
            }
            String value = headers.getFirst(headerName);
            if (value != null && !value.isBlank()) {
                metadata.put("requestHeader." + headerName, value);
            }
        }
        return Map.copyOf(metadata);
    }

    private boolean isTokenHeader(String headerName) {
        return headerName != null
                && headerName.trim().equalsIgnoreCase(properties.getTokenHeader().trim());
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

    @ExceptionHandler(SuspendedRunNotClaimableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ControlPlaneErrorResponse handleNotClaimable(SuspendedRunNotClaimableException exception) {
        return ControlPlaneErrorResponse.notClaimable(exception.getMessage());
    }

    public record RuntimeGoalRequest(
            String input,
            @Nullable Map<String, String> knownParameters
    ) {
        private Map<String, String> knownParametersOrEmpty() {
            return knownParameters == null ? Map.of() : knownParameters;
        }
    }

}
