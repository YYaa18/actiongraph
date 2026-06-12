package com.actiongraph.humanreview.api.spring;

import com.actiongraph.controlplane.api.ControlPlaneErrorResponse;
import com.actiongraph.controlplane.auth.ForbiddenControlPlaneAccessException;
import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;
import com.actiongraph.humanreview.api.HumanReviewApiService;
import com.actiongraph.humanreview.api.HumanReviewTaskNotFoundException;
import com.actiongraph.humanreview.api.HumanReviewTaskResponse;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.StageAlreadyDecidedException;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifier;
import com.actiongraph.spring.security.ActionGraphEndpointGroup;
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

import org.jspecify.annotations.Nullable;

@RestController
@RequestMapping("${actiongraph.human-review.api.path:/actiongraph/human-review/tasks}")
public final class ActionGraphHumanReviewApiController {
    private static final String UNAUTHORIZED_MESSAGE = "Human review API token is missing or invalid";

    private final HumanReviewApiService apiService;
    private final ActionGraphHumanReviewApiProperties properties;
    private final ActionGraphEndpointAccessVerifier accessVerifier;

    public ActionGraphHumanReviewApiController(
            HumanReviewApiService apiService,
            ActionGraphHumanReviewApiProperties properties,
            ActionGraphEndpointAccessVerifier accessVerifier
    ) {
        this.apiService = Objects.requireNonNull(apiService, "apiService");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.accessVerifier = Objects.requireNonNull(accessVerifier, "accessVerifier");
    }

    @GetMapping("/pending")
    public List<HumanReviewTaskResponse> pending(@RequestHeader HttpHeaders headers) {
        verifyAccess(headers);
        return apiService.pendingTasks();
    }

    @GetMapping("/runs/{runId}")
    public List<HumanReviewTaskResponse> tasksForRun(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId
    ) {
        verifyAccess(headers);
        return apiService.tasksForRun(runId);
    }

    @GetMapping("/runs/{runId}/actions/{actionId}")
    public HumanReviewTaskResponse task(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId,
            @PathVariable("actionId") String actionId
    ) {
        verifyAccess(headers);
        return apiService.task(runId, actionId);
    }

    @PostMapping("/runs/{runId}/actions/{actionId}/decision")
    public HumanReviewTaskResponse decide(
            @RequestHeader HttpHeaders headers,
            @PathVariable("runId") String runId,
            @PathVariable("actionId") String actionId,
            @RequestBody HumanReviewDecisionRequest request
    ) {
        RunPrincipal actedBy = verifyAccess(headers);
        return apiService.decide(
                runId,
                actionId,
                request.expectedStageIndex(),
                request.decision(),
                reviewer(request.reviewer(), actedBy),
                request.comment()
        );
    }

    private RunPrincipal verifyAccess(HttpHeaders headers) {
        return accessVerifier.verify(
                ActionGraphEndpointGroup.HUMAN_REVIEW,
                properties,
                headers::getFirst,
                UNAUTHORIZED_MESSAGE
        );
    }

    private String reviewer(@Nullable String requestedReviewer, RunPrincipal actedBy) {
        if (actedBy != null && !actedBy.anonymousPrincipal()) {
            return actedBy.subject();
        }
        return requestedReviewer;
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

    @ExceptionHandler(StageAlreadyDecidedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ControlPlaneErrorResponse handleConflict(StageAlreadyDecidedException exception) {
        return ControlPlaneErrorResponse.conflict(exception.getMessage());
    }

    @ExceptionHandler(HumanReviewTaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ControlPlaneErrorResponse handleNotFound(HumanReviewTaskNotFoundException exception) {
        return ControlPlaneErrorResponse.notFound(exception.getMessage());
    }

    public record HumanReviewDecisionRequest(
            @Nullable Integer expectedStageIndex,
            HumanReviewDecision decision,
            @Nullable String reviewer,
            @Nullable String comment
    ) {
    }

}
