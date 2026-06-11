package com.actiongraph.exception;

import java.util.Objects;

/**
 * Failure raised when a requested ActionGraph resource cannot be found.
 */
public class ActionGraphNotFoundException extends ActionGraphException {
    private static final long serialVersionUID = 1L;

    private final String resourceType;
    private final String resourceId;

    public ActionGraphNotFoundException(String resourceType, String resourceId) {
        this(resourceType, resourceId, resourceType + " not found: " + resourceId);
    }

    public ActionGraphNotFoundException(String resourceType, String resourceId, String message) {
        super(message);
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }
}
