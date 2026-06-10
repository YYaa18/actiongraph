package com.actiongraph.runtime.api.spring;

import com.actiongraph.runtime.SuspendedRunNotClaimableException;
import com.actiongraph.runtime.api.ActionGraphRuntimeApiService;
import com.actiongraph.runtime.api.RuntimeInterpretationResponse;
import com.actiongraph.runtime.api.RuntimeRunResponse;
import com.actiongraph.runtime.api.RuntimeStartResponse;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.runtime.api.path:/actiongraph/runtime}")
public final class ActionGraphRuntimeApiController {
    private final ActionGraphRuntimeApiService apiService;
    private final ActionGraphRuntimeApiProperties properties;

    public ActionGraphRuntimeApiController(
            ActionGraphRuntimeApiService apiService,
            ActionGraphRuntimeApiProperties properties
    ) {
        this.apiService = Objects.requireNonNull(apiService, "apiService");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @PostMapping("/interpret")
    public RuntimeInterpretationResponse interpret(
            @RequestHeader HttpHeaders headers,
            @RequestBody RuntimeGoalRequest request
    ) {
        verifyToken(headers);
        return apiService.interpret(request.input(), request.knownParametersOrEmpty());
    }

    @PostMapping("/runs")
    public RuntimeStartResponse start(
            @RequestHeader HttpHeaders headers,
            @RequestBody RuntimeGoalRequest request
    ) {
        verifyToken(headers);
        return apiService.start(request.input(), request.knownParametersOrEmpty());
    }

    @PostMapping("/runs/{runId}/resume")
    public RuntimeRunResponse resume(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId
    ) {
        verifyToken(headers);
        return apiService.resume(runId);
    }

    private void verifyToken(HttpHeaders headers) {
        if (!properties.hasSharedSecret()) {
            return;
        }
        String actual = headers.getFirst(properties.getTokenHeader());
        if (!sameSecret(properties.getSharedSecret(), actual)) {
            throw new UnauthorizedRuntimeApiException();
        }
    }

    private boolean sameSecret(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual == null
                ? new byte[0]
                : actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    @ExceptionHandler(UnauthorizedRuntimeApiException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public RuntimeApiErrorResponse handleUnauthorized(UnauthorizedRuntimeApiException exception) {
        return new RuntimeApiErrorResponse("UNAUTHORIZED", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RuntimeApiErrorResponse handleBadRequest(IllegalArgumentException exception) {
        return new RuntimeApiErrorResponse("BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(SuspendedRunNotClaimableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public RuntimeApiErrorResponse handleNotClaimable(SuspendedRunNotClaimableException exception) {
        return new RuntimeApiErrorResponse("NOT_CLAIMABLE", exception.getMessage());
    }

    public record RuntimeGoalRequest(
            String input,
            Map<String, String> knownParameters
    ) {
        private Map<String, String> knownParametersOrEmpty() {
            return knownParameters == null ? Map.of() : knownParameters;
        }
    }

    public record RuntimeApiErrorResponse(
            String error,
            String message
    ) {
    }

    private static final class UnauthorizedRuntimeApiException extends RuntimeException {
        private UnauthorizedRuntimeApiException() {
            super("Runtime API token is missing or invalid");
        }
    }
}
