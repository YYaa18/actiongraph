package com.actiongraph.policy;

/**
 * Application-owned adapter for routing a pending action to human review.
 *
 * <p>The policy may create or correlate an approval task in an external system,
 * or it may approve/deny synchronously in tests and controlled automation. It
 * must not execute the business action itself; the runtime resumes execution
 * only after a returned approval decision or a later resume call.
 *
 * <p>Thread-safety depends on the implementation. Production policies that are
 * registered as singletons should avoid per-run mutable fields or protect them
 * explicitly.
 */
public interface HumanReviewPolicy {
    /**
     * Reviews a pending action request.
     *
     * @param request masked review request; never {@code null}
     * @return non-null review result
     */
    HumanReviewResult review(HumanReviewRequest request);
}
