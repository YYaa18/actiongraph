package com.actiongraph.catalog.spring;

import com.actiongraph.catalog.ActionGraphComponent;
import com.actiongraph.catalog.ActionGraphComponentCatalog;
import com.actiongraph.catalog.ActionGraphComponentCatalogService;
import com.actiongraph.catalog.ActionGraphCompositionProfile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.component-catalog.path:/actiongraph/components}")
public final class ActionGraphComponentCatalogController {
    private final ActionGraphComponentCatalogService catalogService;
    private final ActionGraphComponentCatalogProperties properties;

    public ActionGraphComponentCatalogController(
            ActionGraphComponentCatalogService catalogService,
            ActionGraphComponentCatalogProperties properties
    ) {
        this.catalogService = Objects.requireNonNull(catalogService, "catalogService");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @GetMapping
    public ActionGraphComponentCatalog catalog(@RequestHeader HttpHeaders headers) {
        verifyToken(headers);
        return catalogService.catalog();
    }

    @GetMapping("/modules")
    public List<ActionGraphComponent> modules(@RequestHeader HttpHeaders headers) {
        verifyToken(headers);
        return catalogService.components();
    }

    @GetMapping("/modules/{module}")
    public ActionGraphComponent module(
            @RequestHeader HttpHeaders headers,
            @PathVariable("module") String module
    ) {
        verifyToken(headers);
        return catalogService.component(module)
                .orElseThrow(() -> new ComponentCatalogEntryNotFoundException("Component not found: " + module));
    }

    @GetMapping("/profiles")
    public List<ActionGraphCompositionProfile> profiles(@RequestHeader HttpHeaders headers) {
        verifyToken(headers);
        return catalogService.profiles();
    }

    @GetMapping("/profiles/{profile}")
    public ActionGraphCompositionProfile profile(
            @RequestHeader HttpHeaders headers,
            @PathVariable("profile") String profile
    ) {
        verifyToken(headers);
        return catalogService.profile(profile)
                .orElseThrow(() -> new ComponentCatalogEntryNotFoundException("Profile not found: " + profile));
    }

    private void verifyToken(HttpHeaders headers) {
        if (!properties.hasSharedSecret()) {
            return;
        }
        String actual = headers.getFirst(properties.getTokenHeader());
        if (!sameSecret(properties.getSharedSecret(), actual)) {
            throw new UnauthorizedComponentCatalogException();
        }
    }

    private boolean sameSecret(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual == null
                ? new byte[0]
                : actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    @ExceptionHandler(UnauthorizedComponentCatalogException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ComponentCatalogErrorResponse handleUnauthorized(UnauthorizedComponentCatalogException exception) {
        return new ComponentCatalogErrorResponse("UNAUTHORIZED", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ComponentCatalogErrorResponse handleBadRequest(IllegalArgumentException exception) {
        return new ComponentCatalogErrorResponse("BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(ComponentCatalogEntryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ComponentCatalogErrorResponse handleNotFound(ComponentCatalogEntryNotFoundException exception) {
        return new ComponentCatalogErrorResponse("NOT_FOUND", exception.getMessage());
    }

    public record ComponentCatalogErrorResponse(
            String error,
            String message
    ) {
    }

    private static final class UnauthorizedComponentCatalogException extends RuntimeException {
        private UnauthorizedComponentCatalogException() {
            super("Component catalog token is missing or invalid");
        }
    }
}
