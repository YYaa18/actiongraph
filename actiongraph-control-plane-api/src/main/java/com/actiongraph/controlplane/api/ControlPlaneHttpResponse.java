package com.actiongraph.controlplane.api;

public final class ControlPlaneHttpResponse {
    private final int statusCode;
    private final String body;

    public ControlPlaneHttpResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
    }

    public int statusCode() {
        return statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String body() {
        return body;
    }

    public String getBody() {
        return body;
    }

    public boolean successful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean isSuccessful() {
        return successful();
    }
}
