package com.actiongraph.controlplane.api;

import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

public final class ActionGraphControlPlaneHttpClient {
    private static final String RUNTIME_PATH = "/runtime";
    private static final String CATALOG_PATH = "/components";
    private static final String REVIEW_TASKS_PATH = "/human-review/tasks";
    private static final String REVIEW_CALLBACKS_PATH = "/human-review/callbacks";
    private static final String CONSOLE_PATH = "/console";

    private final @Nullable ActionGraphRuntimeHttpClient runtime;
    private final @Nullable ActionGraphComponentCatalogHttpClient catalog;
    private final @Nullable ActionGraphHumanReviewHttpClient humanReview;
    private final @Nullable ActionGraphConsoleHttpClient console;

    private ActionGraphControlPlaneHttpClient(Builder builder) {
        String actionGraphBaseUrl = trimTrailingSlash(builder.actionGraphBaseUrl);
        String runtimeApiBaseUrl = coalesce(builder.runtimeApiBaseUrl, actionGraphBaseUrl, RUNTIME_PATH);
        String catalogApiBaseUrl = coalesce(builder.catalogApiBaseUrl, actionGraphBaseUrl, CATALOG_PATH);
        String reviewTaskApiBaseUrl = coalesce(builder.reviewTaskApiBaseUrl, actionGraphBaseUrl, REVIEW_TASKS_PATH);
        String reviewCallbackApiBaseUrl = coalesce(
                builder.reviewCallbackApiBaseUrl, actionGraphBaseUrl, REVIEW_CALLBACKS_PATH);
        String consoleApiBaseUrl = coalesce(builder.consoleApiBaseUrl, actionGraphBaseUrl, CONSOLE_PATH);

        if (isBlank(reviewTaskApiBaseUrl) && !isBlank(reviewCallbackApiBaseUrl)) {
            throw new IllegalArgumentException("reviewTaskApiBaseUrl must be configured before reviewCallbackApiBaseUrl");
        }
        if (isBlank(runtimeApiBaseUrl)
                && isBlank(catalogApiBaseUrl)
                && isBlank(reviewTaskApiBaseUrl)
                && isBlank(consoleApiBaseUrl)) {
            throw new IllegalArgumentException("At least one control-plane endpoint base URL must be configured");
        }

        this.runtime = isBlank(runtimeApiBaseUrl) ? null : ActionGraphRuntimeHttpClient
                .builder(runtimeApiBaseUrl)
                .tokenHeader(builder.runtimeTokenHeader)
                .sharedSecret(builder.runtimeSharedSecret)
                .connectTimeoutMillis(builder.connectTimeoutMillis)
                .readTimeoutMillis(builder.readTimeoutMillis)
                .defaultHeaders(builder.defaultHeaders)
                .build();
        this.catalog = isBlank(catalogApiBaseUrl) ? null : ActionGraphComponentCatalogHttpClient
                .builder(catalogApiBaseUrl)
                .tokenHeader(builder.catalogTokenHeader)
                .sharedSecret(builder.catalogSharedSecret)
                .connectTimeoutMillis(builder.connectTimeoutMillis)
                .readTimeoutMillis(builder.readTimeoutMillis)
                .maxGetRetries(builder.maxGetRetries)
                .getRetryBackoffMillis(builder.getRetryBackoffMillis)
                .defaultHeaders(builder.defaultHeaders)
                .build();
        if (isBlank(reviewTaskApiBaseUrl)) {
            this.humanReview = null;
        } else {
            ActionGraphHumanReviewHttpClient.Builder reviewBuilder = ActionGraphHumanReviewHttpClient
                    .builder(reviewTaskApiBaseUrl)
                    .tokenHeader(builder.reviewTokenHeader)
                    .sharedSecret(builder.reviewSharedSecret)
                    .connectTimeoutMillis(builder.connectTimeoutMillis)
                    .readTimeoutMillis(builder.readTimeoutMillis)
                    .maxGetRetries(builder.maxGetRetries)
                    .getRetryBackoffMillis(builder.getRetryBackoffMillis)
                    .defaultHeaders(builder.defaultHeaders);
            if (!isBlank(reviewCallbackApiBaseUrl)) {
                reviewBuilder.callbackApiBaseUrl(reviewCallbackApiBaseUrl);
            }
            this.humanReview = reviewBuilder.build();
        }
        this.console = isBlank(consoleApiBaseUrl) ? null : ActionGraphConsoleHttpClient
                .builder(consoleApiBaseUrl)
                .tokenHeader(builder.consoleTokenHeader)
                .sharedSecret(builder.consoleSharedSecret)
                .connectTimeoutMillis(builder.connectTimeoutMillis)
                .readTimeoutMillis(builder.readTimeoutMillis)
                .maxGetRetries(builder.maxGetRetries)
                .getRetryBackoffMillis(builder.getRetryBackoffMillis)
                .defaultHeaders(builder.defaultHeaders)
                .build();
    }

    public static Builder builder() {
        return new Builder(null);
    }

    public static Builder builder(@Nullable String actionGraphBaseUrl) {
        return new Builder(actionGraphBaseUrl);
    }

    public boolean hasRuntime() {
        return runtime != null;
    }

    public boolean hasCatalog() {
        return catalog != null;
    }

    public boolean hasHumanReview() {
        return humanReview != null;
    }

    public boolean hasConsole() {
        return console != null;
    }

    public ActionGraphRuntimeHttpClient runtime() {
        return requireConfigured(runtime, "runtime");
    }

    public ActionGraphComponentCatalogHttpClient catalog() {
        return requireConfigured(catalog, "component catalog");
    }

    public ActionGraphHumanReviewHttpClient humanReview() {
        return requireConfigured(humanReview, "human review");
    }

    public ActionGraphConsoleHttpClient console() {
        return requireConfigured(console, "console");
    }

    private static <T> T requireConfigured(@Nullable T value, String surface) {
        if (value == null) {
            throw new IllegalStateException("ActionGraph " + surface + " endpoint is not configured");
        }
        return value;
    }

    private static @Nullable String coalesce(
            @Nullable String configuredBaseUrl,
            @Nullable String actionGraphBaseUrl,
            String path
    ) {
        if (!isBlank(configuredBaseUrl)) {
            return configuredBaseUrl;
        }
        if (isBlank(actionGraphBaseUrl)) {
            return null;
        }
        return actionGraphBaseUrl + path;
    }

    private static @Nullable String trimTrailingSlash(@Nullable String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String requireText(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Builder {
        private @Nullable String actionGraphBaseUrl;
        private @Nullable String runtimeApiBaseUrl;
        private @Nullable String catalogApiBaseUrl;
        private @Nullable String reviewTaskApiBaseUrl;
        private @Nullable String reviewCallbackApiBaseUrl;
        private @Nullable String consoleApiBaseUrl;
        private String runtimeTokenHeader = ActionGraphRuntimeHttpClient.DEFAULT_RUNTIME_TOKEN_HEADER;
        private String catalogTokenHeader = ActionGraphComponentCatalogHttpClient.DEFAULT_CATALOG_TOKEN_HEADER;
        private String reviewTokenHeader = ActionGraphHumanReviewHttpClient.DEFAULT_REVIEW_TOKEN_HEADER;
        private String consoleTokenHeader = ActionGraphConsoleHttpClient.DEFAULT_CONSOLE_TOKEN_HEADER;
        private @Nullable String runtimeSharedSecret = "";
        private @Nullable String catalogSharedSecret = "";
        private @Nullable String reviewSharedSecret = "";
        private @Nullable String consoleSharedSecret = "";
        private int connectTimeoutMillis = 5000;
        private int readTimeoutMillis = 30000;
        private int maxGetRetries = 0;
        private int getRetryBackoffMillis = 0;
        private final Map<String, String> defaultHeaders = new TreeMap<String, String>();

        private Builder(@Nullable String actionGraphBaseUrl) {
            this.actionGraphBaseUrl = actionGraphBaseUrl;
        }

        public Builder actionGraphBaseUrl(@Nullable String actionGraphBaseUrl) {
            this.actionGraphBaseUrl = actionGraphBaseUrl;
            return this;
        }

        public Builder runtimeApiBaseUrl(@Nullable String runtimeApiBaseUrl) {
            this.runtimeApiBaseUrl = runtimeApiBaseUrl;
            return this;
        }

        public Builder catalogApiBaseUrl(@Nullable String catalogApiBaseUrl) {
            this.catalogApiBaseUrl = catalogApiBaseUrl;
            return this;
        }

        public Builder reviewTaskApiBaseUrl(@Nullable String reviewTaskApiBaseUrl) {
            this.reviewTaskApiBaseUrl = reviewTaskApiBaseUrl;
            return this;
        }

        public Builder reviewCallbackApiBaseUrl(@Nullable String reviewCallbackApiBaseUrl) {
            this.reviewCallbackApiBaseUrl = reviewCallbackApiBaseUrl;
            return this;
        }

        public Builder consoleApiBaseUrl(@Nullable String consoleApiBaseUrl) {
            this.consoleApiBaseUrl = consoleApiBaseUrl;
            return this;
        }

        public Builder tokenHeader(String tokenHeader) {
            String safeTokenHeader = requireText(tokenHeader, "tokenHeader");
            this.runtimeTokenHeader = safeTokenHeader;
            this.catalogTokenHeader = safeTokenHeader;
            this.reviewTokenHeader = safeTokenHeader;
            this.consoleTokenHeader = safeTokenHeader;
            return this;
        }

        public Builder runtimeTokenHeader(String runtimeTokenHeader) {
            this.runtimeTokenHeader = runtimeTokenHeader;
            return this;
        }

        public Builder catalogTokenHeader(String catalogTokenHeader) {
            this.catalogTokenHeader = catalogTokenHeader;
            return this;
        }

        public Builder reviewTokenHeader(String reviewTokenHeader) {
            this.reviewTokenHeader = reviewTokenHeader;
            return this;
        }

        public Builder consoleTokenHeader(String consoleTokenHeader) {
            this.consoleTokenHeader = consoleTokenHeader;
            return this;
        }

        public Builder sharedSecret(@Nullable String sharedSecret) {
            this.runtimeSharedSecret = sharedSecret;
            this.catalogSharedSecret = sharedSecret;
            this.reviewSharedSecret = sharedSecret;
            this.consoleSharedSecret = sharedSecret;
            return this;
        }

        public Builder runtimeSharedSecret(@Nullable String runtimeSharedSecret) {
            this.runtimeSharedSecret = runtimeSharedSecret;
            return this;
        }

        public Builder catalogSharedSecret(@Nullable String catalogSharedSecret) {
            this.catalogSharedSecret = catalogSharedSecret;
            return this;
        }

        public Builder reviewSharedSecret(@Nullable String reviewSharedSecret) {
            this.reviewSharedSecret = reviewSharedSecret;
            return this;
        }

        public Builder consoleSharedSecret(@Nullable String consoleSharedSecret) {
            this.consoleSharedSecret = consoleSharedSecret;
            return this;
        }

        public Builder connectTimeoutMillis(int connectTimeoutMillis) {
            if (connectTimeoutMillis < 0) {
                throw new IllegalArgumentException("connectTimeoutMillis must not be negative");
            }
            this.connectTimeoutMillis = connectTimeoutMillis;
            return this;
        }

        public Builder readTimeoutMillis(int readTimeoutMillis) {
            if (readTimeoutMillis < 0) {
                throw new IllegalArgumentException("readTimeoutMillis must not be negative");
            }
            this.readTimeoutMillis = readTimeoutMillis;
            return this;
        }

        public Builder maxGetRetries(int maxGetRetries) {
            if (maxGetRetries < 0) {
                throw new IllegalArgumentException("maxGetRetries must not be negative");
            }
            this.maxGetRetries = maxGetRetries;
            return this;
        }

        public Builder getRetryBackoffMillis(int getRetryBackoffMillis) {
            if (getRetryBackoffMillis < 0) {
                throw new IllegalArgumentException("getRetryBackoffMillis must not be negative");
            }
            this.getRetryBackoffMillis = getRetryBackoffMillis;
            return this;
        }

        public Builder defaultHeader(String name, @Nullable String value) {
            this.defaultHeaders.put(requireText(name, "default header name"), value == null ? "" : value);
            return this;
        }

        public Builder defaultHeaders(@Nullable Map<String, String> headers) {
            if (headers == null) {
                return this;
            }
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                defaultHeader(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public ActionGraphControlPlaneHttpClient build() {
            return new ActionGraphControlPlaneHttpClient(this);
        }
    }
}
