package com.actiongraph.policy;

import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Masks trace and review payload data before it leaves the runtime boundary.
 *
 * <p>Implementations are application-owned policy components. They should be
 * deterministic and safe to reuse across concurrent runs. The runtime applies
 * masking before hash calculation, so changing a masking policy changes future
 * audit payloads but does not rewrite existing trace rows.
 *
 * <p>Null contract: implementations must accept {@code null} defensively and
 * return non-null values. Returning raw secrets, tokens, account numbers, or
 * unredacted PII defeats the audit contract.
 */
public interface DataMaskingPolicy {
    /**
     * Masks a free-form trace or review message.
     *
     * @param text raw text, possibly {@code null}
     * @return masked text, never {@code null}
     */
    String maskText(@Nullable String text);

    /**
     * Masks structured trace or review attributes.
     *
     * @param data raw key/value data, possibly {@code null}
     * @return masked key/value data, never {@code null}
     */
    Map<String, String> maskData(@Nullable Map<String, String> data);
}
