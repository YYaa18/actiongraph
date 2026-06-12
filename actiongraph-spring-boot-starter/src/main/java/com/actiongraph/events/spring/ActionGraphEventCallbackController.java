package com.actiongraph.events.spring;

import com.actiongraph.controlplane.api.ControlPlaneErrorResponse;
import com.actiongraph.controlplane.auth.ForbiddenControlPlaneAccessException;
import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;
import com.actiongraph.events.DeliveryResult;
import com.actiongraph.events.EventPayload;
import com.actiongraph.events.ExternalEventGateway;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.spring.ActionGraphProperties;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifier;
import com.actiongraph.spring.security.ActionGraphEndpointGroup;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.events.callback-endpoint.path:/actiongraph/events}")
public final class ActionGraphEventCallbackController {
    private static final String UNAUTHORIZED_MESSAGE = "Event callback token is missing or invalid";

    private final ExternalEventGateway gateway;
    private final ActionGraphProperties properties;
    private final ActionGraphEndpointAccessVerifier accessVerifier;

    public ActionGraphEventCallbackController(
            ExternalEventGateway gateway,
            ActionGraphProperties properties,
            ActionGraphEndpointAccessVerifier accessVerifier
    ) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.accessVerifier = Objects.requireNonNull(accessVerifier, "accessVerifier");
    }

    @PostMapping("/{eventType}/{correlationId}")
    public EventCallbackResponse handle(
            @RequestHeader HttpHeaders headers,
            @PathVariable("eventType") String eventType,
            @PathVariable("correlationId") String correlationId,
            @RequestBody(required = false) String body
    ) {
        RunPrincipal actedBy = verifyAccess(headers);
        DeliveryResult result = gateway.deliver(
                eventType,
                correlationId,
                new EventPayload(contentType(headers), body == null ? "" : body, java.util.Map.of()),
                actedBy
        );
        return new EventCallbackResponse(eventType, correlationId, result);
    }

    private String contentType(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        return contentType == null ? "application/octet-stream" : contentType.toString();
    }

    private RunPrincipal verifyAccess(HttpHeaders headers) {
        return accessVerifier.verify(
                ActionGraphEndpointGroup.EVENTS,
                properties.getEvents().getCallbackEndpoint(),
                headers::getFirst,
                UNAUTHORIZED_MESSAGE
        );
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

    public record EventCallbackResponse(String eventType, String correlationId, DeliveryResult result) {
    }
}
