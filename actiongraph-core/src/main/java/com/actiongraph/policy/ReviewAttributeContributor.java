package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.runtime.Blackboard;

import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Adds application-specific attributes to a human-review request.
 *
 * <p>Contributors are useful for approval-system routing fields such as tenant,
 * branch, amount band, customer tier, or case priority. Returned data is passed
 * through {@link DataMaskingPolicy} before it is stored or displayed, but
 * contributors should still avoid returning secrets.
 *
 * <p>Null contract: implementations should return a non-null map. The runtime
 * tolerates {@code null} as no attributes for backward compatibility.
 */
public interface ReviewAttributeContributor {
    /**
     * Produces additional review attributes for the pending action.
     *
     * @param action pending action; never {@code null}
     * @param blackboard current run Blackboard; never {@code null}
     * @return attribute map, or {@code null} to add no attributes
     */
    @Nullable
    Map<String, String> contribute(Action action, Blackboard blackboard);
}
