package com.actiongraph.humanreview.api.spring;

import com.actiongraph.controlplane.auth.ControlPlaneTokenVerifier;
import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;
import com.actiongraph.humanreview.api.HumanReviewApiService;
import com.actiongraph.humanreview.api.HumanReviewTaskNotFoundException;
import com.actiongraph.humanreview.api.HumanReviewTaskResponse;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.StageAlreadyDecidedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.human-review.api.path:/actiongraph/human-review/tasks}")
public final class ActionGraphHumanReviewApiController {
    private static final ControlPlaneTokenVerifier TOKEN_VERIFIER = new ControlPlaneTokenVerifier();
    private static final String UNAUTHORIZED_MESSAGE = "Human review API token is missing or invalid";

    private final HumanReviewApiService apiService;
    private final ActionGraphHumanReviewApiProperties properties;

    public ActionGraphHumanReviewApiController(
            HumanReviewApiService apiService,
            ActionGraphHumanReviewApiProperties properties
    ) {
        this.apiService = Objects.requireNonNull(apiService, "apiService");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @GetMapping("/pending")
    public List<HumanReviewTaskResponse> pending(@RequestHeader HttpHeaders headers) {
        verifyToken(headers);
        return apiService.pendingTasks();
    }

    @GetMapping("/runs/{runId}")
    public List<HumanReviewTaskResponse> tasksForRun(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId
    ) {
        verifyToken(headers);
        return apiService.tasksForRun(runId);
    }

    @GetMapping("/runs/{runId}/actions/{actionId}")
    public HumanReviewTaskResponse task(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId,
            @PathVariable("actionId") String actionId
    ) {
        verifyToken(headers);
        return apiService.task(runId, actionId);
    }

    @PostMapping("/runs/{runId}/actions/{actionId}/decision")
    public HumanReviewTaskResponse decide(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId,
            @PathVariable("actionId") String actionId,
            @RequestBody HumanReviewDecisionRequest request
    ) {
        verifyToken(headers);
        return apiService.decide(
                runId,
                actionId,
                request.expectedStageIndex(),
                request.decision(),
                request.reviewer(),
                request.comment()
        );
    }

    private void verifyToken(HttpHeaders headers) {
        TOKEN_VERIFIER.verify(properties, headers::getFirst, UNAUTHORIZED_MESSAGE);
    }

    @ExceptionHandler(UnauthorizedControlPlaneAccessException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public HumanReviewApiErrorResponse handleUnauthorized(UnauthorizedControlPlaneAccessException exception) {
        return new HumanReviewApiErrorResponse("UNAUTHORIZED", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HumanReviewApiErrorResponse handleBadRequest(IllegalArgumentException exception) {
        return new HumanReviewApiErrorResponse("BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(StageAlreadyDecidedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public HumanReviewApiErrorResponse handleConflict(StageAlreadyDecidedException exception) {
        return new HumanReviewApiErrorResponse("CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(HumanReviewTaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public HumanReviewApiErrorResponse handleNotFound(HumanReviewTaskNotFoundException exception) {
        return new HumanReviewApiErrorResponse("NOT_FOUND", exception.getMessage());
    }

    public record HumanReviewDecisionRequest(
            Integer expectedStageIndex,
            HumanReviewDecision decision,
            String reviewer,
            String comment
    ) {
    }

    public record HumanReviewApiErrorResponse(
            String error,
            String message
    ) {
    }
}
