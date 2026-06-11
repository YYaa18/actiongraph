package com.actiongraph.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public type or member that is exposed for framework wiring,
 * generated metadata, or integration mechanics but is not intended for direct
 * application use.
 *
 * <p>Internal APIs are not compatibility-protected after 1.0 unless another
 * stable contract explicitly says otherwise. Prefer a documented SPI or facade
 * when one exists.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
        ElementType.TYPE,
        ElementType.METHOD,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD,
        ElementType.PACKAGE
})
public @interface Internal {
}
