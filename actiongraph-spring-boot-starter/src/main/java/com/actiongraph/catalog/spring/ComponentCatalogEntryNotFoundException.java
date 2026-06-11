package com.actiongraph.catalog.spring;

import com.actiongraph.exception.ActionGraphNotFoundException;

/**
 * Raised when a Spring component-catalog endpoint is asked for an unknown entry.
 */
public final class ComponentCatalogEntryNotFoundException extends ActionGraphNotFoundException {
    private static final long serialVersionUID = 1L;

    public ComponentCatalogEntryNotFoundException(String resourceType, String resourceId, String message) {
        super(resourceType, resourceId, message);
    }
}
