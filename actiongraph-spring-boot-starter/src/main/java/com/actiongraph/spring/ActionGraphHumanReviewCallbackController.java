package com.actiongraph.spring;

import com.actiongraph.action.ActionId;
import com.actiongraph.policy.HumanReviewCallback;
import com.actiongraph.policy.HumanReviewCallbackHandler;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewTask;
import com.actiongraph.policy.StageAlreadyDecidedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.human-review.callback-endpoint.path:/actiongraph/human-review/callbacks}")
public final class ActionGraphHumanReviewCallbackController {
    private final HumanReviewCallbackHandler handler;

    public ActionGraphHumanReviewCallbackController(HumanReviewCallbackHandler handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @PostMapping
    public HumanReviewCallbackResponse handle(@RequestBody HumanReviewCallbackRequest request) {
        HumanReviewTask task = handler.handle(new HumanReviewCallback(
                request.runId(),
                new ActionId(request.actionId()),
                request.expectedStageIndex(),
                request.decision(),
                request.reviewer(),
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

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HumanReviewCallbackErrorResponse handleBadRequest(IllegalArgumentException exception) {
        return new HumanReviewCallbackErrorResponse("BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(StageAlreadyDecidedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public HumanReviewCallbackErrorResponse handleConflict(StageAlreadyDecidedException exception) {
        return new HumanReviewCallbackErrorResponse("CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public HumanReviewCallbackErrorResponse handleNotFound(IllegalStateException exception) {
        return new HumanReviewCallbackErrorResponse("NOT_FOUND", exception.getMessage());
    }

    public record HumanReviewCallbackRequest(
            String runId,
            String actionId,
            int expectedStageIndex,
            HumanReviewDecision decision,
            String reviewer,
            String comment
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

    public record HumanReviewCallbackErrorResponse(
            String error,
            String message
    ) {
    }
}
