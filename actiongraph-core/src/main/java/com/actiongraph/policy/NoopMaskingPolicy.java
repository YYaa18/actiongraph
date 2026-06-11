package com.actiongraph.policy;

import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Masking policy that returns input values unchanged.
 *
 * <p>This is appropriate only when upstream data is already safe for audit and
 * display. It is immutable and safe to share across concurrent runs.
 */
public final class NoopMaskingPolicy implements DataMaskingPolicy {
    public static final NoopMaskingPolicy INSTANCE = new NoopMaskingPolicy();

    public NoopMaskingPolicy() {
    }

    @Override
    public String maskText(@Nullable String text) {
        return text == null ? "" : text;
    }

    @Override
    public Map<String, String> maskData(@Nullable Map<String, String> data) {
        return data == null ? Map.of() : data;
    }
}
