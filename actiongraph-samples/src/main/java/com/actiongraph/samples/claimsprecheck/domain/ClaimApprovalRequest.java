package com.actiongraph.samples.claimsprecheck.domain;

public record ClaimApprovalRequest(String requestId, String draftId) {
    public ClaimApprovalRequest {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (draftId == null || draftId.isBlank()) {
            throw new IllegalArgumentException("draftId must not be blank");
        }
    }
}
