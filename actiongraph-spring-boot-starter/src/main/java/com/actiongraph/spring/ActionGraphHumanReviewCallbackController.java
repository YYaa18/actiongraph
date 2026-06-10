package com.actiongraph.spring;

import com.actiongraph.action.ActionId;
import com.actiongraph.policy.HumanReviewCallback;
import com.actiongraph.policy.HumanReviewCallbackHandler;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewTask;
import com.actiongraph.policy.StageAlreadyDecidedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.human-review.callback-endpoint.path:/actiongraph/human-review/callbacks}")
public final class ActionGraphHumanReviewCallbackController {
    private final HumanReviewCallbackHandler handler;
    private final ActionGraphProperties.CallbackEndpointProperties properties;

    public ActionGraphHumanReviewCallbackController(
            HumanReviewCallbackHandler handler,
            ActionGraphProperties.CallbackEndpointProperties properties
    ) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @PostMapping
    public HumanReviewCallbackResponse handle(
            @RequestHeader HttpHeaders headers,
            @RequestBody HumanReviewCallbackRequest request
    ) {
        verifyToken(headers);
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

    private void verifyToken(HttpHeaders headers) {
        if (!properties.hasSharedSecret()) {
            return;
        }
        String actual = headers.getFirst(properties.getTokenHeader());
        if (!sameSecret(properties.getSharedSecret(), actual)) {
            throw new UnauthorizedCallbackException();
        }
    }

    private boolean sameSecret(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual == null
                ? new byte[0]
                : actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    @ExceptionHandler(UnauthorizedCallbackException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public HumanReviewCallbackErrorResponse handleUnauthorized(UnauthorizedCallbackException exception) {
        return new HumanReviewCallbackErrorResponse("UNAUTHORIZED", exception.getMessage());
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

    private static final class UnauthorizedCallbackException extends RuntimeException {
        private UnauthorizedCallbackException() {
            super("Human review callback token is missing or invalid");
        }
    }
}
