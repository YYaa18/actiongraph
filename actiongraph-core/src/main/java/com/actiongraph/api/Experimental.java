package com.actiongraph.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public ActionGraph API that is intentionally available but not yet
 * covered by the full post-1.0 compatibility promise.
 *
 * <p>Experimental APIs may change in minor releases before they are promoted to
 * the stable contract. They should still be documented and tested; the marker
 * exists so application teams can make an explicit adoption choice.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
        ElementType.TYPE,
        ElementType.METHOD,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD,
        ElementType.PACKAGE,
        ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT
})
public @interface Experimental {
    /**
     * Version or milestone where the API was introduced as experimental.
     */
    String since() default "";

    /**
     * Human-readable explanation of the compatibility risk.
     */
    String value() default "";
}
