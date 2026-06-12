package com.actiongraph.console.studio.spring;

import com.actiongraph.api.Experimental;
import com.actiongraph.console.studio.GoalStudioService;
import com.actiongraph.console.studio.GoalStudioSessionResponse;
import com.actiongraph.controlplane.api.ControlPlaneErrorResponse;
import com.actiongraph.controlplane.auth.ForbiddenControlPlaneAccessException;
import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.identity.RunPrincipal;
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

import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.studio.path:/actiongraph/studio}")
@Experimental(
        since = "0.2.0",
        value = "Goal Studio HTTP endpoints are experimental and must remain disabled outside drafting environments."
)
public final class ActionGraphStudioController {
    private static final String UNAUTHORIZED_MESSAGE = "Studio token is missing or invalid";

    private final GoalStudioService studioService;
    private final ActionGraphStudioProperties properties;
    private final ActionGraphEndpointAccessVerifier accessVerifier;

    public ActionGraphStudioController(
            GoalStudioService studioService,
            ActionGraphStudioProperties properties,
            ActionGraphEndpointAccessVerifier accessVerifier
    ) {
        this.studioService = Objects.requireNonNull(studioService, "studioService");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.accessVerifier = Objects.requireNonNull(accessVerifier, "accessVerifier");
    }

    @PostMapping("/sessions")
    public GoalStudioSessionResponse create(
            @RequestHeader HttpHeaders headers,
            @RequestBody CreateSessionRequest request
    ) {
        verifyAccess(headers);
        return studioService.createSession(request.description());
    }

    @PostMapping("/sessions/{id}/refine")
    public GoalStudioSessionResponse refine(
            @RequestHeader HttpHeaders headers,
            @PathVariable("id") String id,
            @RequestBody RefineSessionRequest request
    ) {
        verifyAccess(headers);
        return studioService.refine(id, request.feedback());
    }

    @PostMapping("/sessions/{id}/approve")
    public GoalStudioSessionResponse approve(
            @RequestHeader HttpHeaders headers,
            @PathVariable("id") String id,
            @RequestBody ApproveSessionRequest request
    ) {
        RunPrincipal principal = verifyAccess(headers);
        return studioService.approve(id, approver(request.approver(), principal));
    }

    @GetMapping("/sessions/{id}")
    public GoalStudioSessionResponse session(
            @RequestHeader HttpHeaders headers,
            @PathVariable("id") String id
    ) {
        verifyAccess(headers);
        return studioService.session(id);
    }

    private RunPrincipal verifyAccess(HttpHeaders headers) {
        return accessVerifier.verify(ActionGraphEndpointGroup.STUDIO, properties, headers::getFirst,
                UNAUTHORIZED_MESSAGE);
    }

    private String approver(String requestedApprover, RunPrincipal principal) {
        if (principal != null && !principal.anonymousPrincipal()) {
            return principal.subject();
        }
        return requestedApprover;
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

    @ExceptionHandler({IllegalArgumentException.class, ActionGraphConfigurationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ControlPlaneErrorResponse handleBadRequest(RuntimeException exception) {
        return ControlPlaneErrorResponse.badRequest(exception.getMessage());
    }

    public record CreateSessionRequest(String description) {
    }

    public record RefineSessionRequest(String feedback) {
    }

    public record ApproveSessionRequest(String approver) {
    }
}
