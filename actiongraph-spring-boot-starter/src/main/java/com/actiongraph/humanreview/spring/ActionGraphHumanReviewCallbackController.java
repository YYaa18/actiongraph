package com.actiongraph.humanreview.spring;

import com.actiongraph.action.ActionId;
import com.actiongraph.controlplane.api.ControlPlaneErrorResponse;
import com.actiongraph.controlplane.auth.ForbiddenControlPlaneAccessException;
import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.policy.HumanReviewCallback;
import com.actiongraph.policy.HumanReviewCallbackHandler;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewTask;
import com.actiongraph.policy.StageAlreadyDecidedException;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifier;
import com.actiongraph.spring.security.ActionGraphEndpointGroup;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

@RestController
@RequestMapping("${actiongraph.human-review.callback-endpoint.path:/actiongraph/human-review/callbacks}")
public final class ActionGraphHumanReviewCallbackController {
    private static final String UNAUTHORIZED_MESSAGE = "Human review callback token is missing or invalid";

    private final HumanReviewCallbackHandler handler;
    private final ActionGraphHumanReviewCallbackProperties properties;
    private final ActionGraphEndpointAccessVerifier accessVerifier;

    public ActionGraphHumanReviewCallbackController(
            HumanReviewCallbackHandler handler,
            ActionGraphHumanReviewCallbackProperties properties,
            ActionGraphEndpointAccessVerifier accessVerifier
    ) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.accessVerifier = Objects.requireNonNull(accessVerifier, "accessVerifier");
    }

    @PostMapping
    public HumanReviewCallbackResponse handle(
            @RequestHeader HttpHeaders headers,
            @RequestBody HumanReviewCallbackRequest request
    ) {
        RunPrincipal actedBy = verifyAccess(headers);
        HumanReviewTask task = handler.handle(new HumanReviewCallback(
                request.runId(),
                new ActionId(request.actionId()),
                request.expectedStageIndex(),
                request.decision(),
                reviewer(request.reviewer(), actedBy),
                request.comment()
        ));
        return new HumanReviewCallbackResponse(
                task.runId(),
                task.actionId().value(),
                task.decision(),
                task.currentStageIndex(),
                task.reviewer(),
                task.message(),
                task.stageDecisions().size()
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

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ControlPlaneErrorResponse handleNotFound(IllegalStateException exception) {
        return ControlPlaneErrorResponse.notFound(exception.getMessage());
    }

    public record HumanReviewCallbackRequest(
            String runId,
            String actionId,
            int expectedStageIndex,
            HumanReviewDecision decision,
            @Nullable String reviewer,
            @Nullable String comment
    ) {
    }

    public record HumanReviewCallbackResponse(
            String runId,
            String actionId,
            HumanReviewDecision decision,
            int currentStageIndex,
            String reviewer,
            String message,
            int stageDecisionCount
    ) {
    }

}
