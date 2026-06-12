package com.actiongraph.catalog.spring;

import com.actiongraph.catalog.ActionGraphComponent;
import com.actiongraph.catalog.ActionGraphComponentCatalog;
import com.actiongraph.catalog.ActionGraphComponentCatalogService;
import com.actiongraph.catalog.ActionGraphCompositionProfile;
import com.actiongraph.controlplane.api.ControlPlaneErrorResponse;
import com.actiongraph.controlplane.auth.ForbiddenControlPlaneAccessException;
import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifier;
import com.actiongraph.spring.security.ActionGraphEndpointGroup;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.component-catalog.path:/actiongraph/components}")
public final class ActionGraphComponentCatalogController {
    private static final String UNAUTHORIZED_MESSAGE = "Component catalog token is missing or invalid";

    private final ActionGraphComponentCatalogService catalogService;
    private final ActionGraphComponentCatalogProperties properties;
    private final ActionGraphEndpointAccessVerifier accessVerifier;

    public ActionGraphComponentCatalogController(
            ActionGraphComponentCatalogService catalogService,
            ActionGraphComponentCatalogProperties properties,
            ActionGraphEndpointAccessVerifier accessVerifier
    ) {
        this.catalogService = Objects.requireNonNull(catalogService, "catalogService");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.accessVerifier = Objects.requireNonNull(accessVerifier, "accessVerifier");
    }

    @GetMapping
    public ActionGraphComponentCatalog catalog(@RequestHeader HttpHeaders headers) {
        verifyAccess(headers);
        return catalogService.catalog();
    }

    @GetMapping("/modules")
    public List<ActionGraphComponent> modules(@RequestHeader HttpHeaders headers) {
        verifyAccess(headers);
        return catalogService.components();
    }

    @GetMapping("/compatibility/{compatibility}")
    public List<ActionGraphComponent> modulesByCompatibility(
            @RequestHeader HttpHeaders headers,
            @PathVariable("compatibility") String compatibility
    ) {
        verifyAccess(headers);
        return catalogService.componentsByCompatibility(compatibility);
    }

    @GetMapping("/modules/{module}")
    public ActionGraphComponent module(
            @RequestHeader HttpHeaders headers,
            @PathVariable("module") String module
    ) {
        verifyAccess(headers);
        return catalogService.component(module)
                .orElseThrow(() -> new ComponentCatalogEntryNotFoundException(
                        "component", module, "Component not found: " + module));
    }

    @GetMapping("/modules/{module}/profiles")
    public List<ActionGraphCompositionProfile> profilesContainingModule(
            @RequestHeader HttpHeaders headers,
            @PathVariable("module") String module
    ) {
        verifyAccess(headers);
        catalogService.component(module)
                .orElseThrow(() -> new ComponentCatalogEntryNotFoundException(
                        "component", module, "Component not found: " + module));
        return catalogService.profilesContainingModule(module);
    }

    @GetMapping("/profiles")
    public List<ActionGraphCompositionProfile> profiles(@RequestHeader HttpHeaders headers) {
        verifyAccess(headers);
        return catalogService.profiles();
    }

    @GetMapping("/profiles/{profile}")
    public ActionGraphCompositionProfile profile(
            @RequestHeader HttpHeaders headers,
            @PathVariable("profile") String profile
    ) {
        verifyAccess(headers);
        return catalogService.profile(profile)
                .orElseThrow(() -> new ComponentCatalogEntryNotFoundException(
                        "profile", profile, "Profile not found: " + profile));
    }

    private void verifyAccess(HttpHeaders headers) {
        accessVerifier.verify(ActionGraphEndpointGroup.CONSOLE, properties, headers::getFirst, UNAUTHORIZED_MESSAGE);
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

    @ExceptionHandler(ComponentCatalogEntryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ControlPlaneErrorResponse handleNotFound(ComponentCatalogEntryNotFoundException exception) {
        return ControlPlaneErrorResponse.notFound(exception.getMessage());
    }
}
