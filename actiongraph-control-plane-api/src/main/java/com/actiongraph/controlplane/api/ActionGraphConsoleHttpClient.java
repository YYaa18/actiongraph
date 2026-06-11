package com.actiongraph.controlplane.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public final class ActionGraphConsoleHttpClient {
    public static final String DEFAULT_CONSOLE_TOKEN_HEADER = "X-ActionGraph-Console-Token";

    private final String consoleApiBaseUrl;
    private final String tokenHeader;
    private final String sharedSecret;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final Map<String, String> defaultHeaders;

    private ActionGraphConsoleHttpClient(Builder builder) {
        this.consoleApiBaseUrl = normalizeBaseUrl(builder.consoleApiBaseUrl);
        this.tokenHeader = requireText(builder.tokenHeader, "tokenHeader");
        this.sharedSecret = builder.sharedSecret == null ? "" : builder.sharedSecret;
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.readTimeoutMillis = builder.readTimeoutMillis;
        this.defaultHeaders = new TreeMap<String, String>(builder.defaultHeaders);
    }

    public static Builder builder(String consoleApiBaseUrl) {
        return new Builder(consoleApiBaseUrl);
    }

    public ControlPlaneHttpResponse runs() throws IOException {
        return runs(null, null, null, null, null);
    }

    public ControlPlaneHttpResponse runs(
            Integer limit,
            Integer offset,
            String status,
            Boolean auditComplete
    ) throws IOException {
        return runs(limit, offset, status, auditComplete, null);
    }

    public ControlPlaneHttpResponse runs(
            Integer limit,
            Integer offset,
            String status,
            Boolean auditComplete,
            Map<String, String> requestHeaders
    ) throws IOException {
        return get("/runs" + runQuery(limit, offset, status, auditComplete),
                "application/json", requestHeaders);
    }

    public ControlPlaneHttpResponse run(String runId) throws IOException {
        return run(runId, null);
    }

    public ControlPlaneHttpResponse run(String runId, Map<String, String> requestHeaders) throws IOException {
        return get("/runs/" + encodePathSegment(requireText(runId, "runId")),
                "application/json", requestHeaders);
    }

    public ControlPlaneHttpResponse trace(String runId) throws IOException {
        return trace(runId, null);
    }

    public ControlPlaneHttpResponse trace(String runId, Map<String, String> requestHeaders) throws IOException {
        return get("/runs/" + encodePathSegment(requireText(runId, "runId")) + "/trace",
                "application/json", requestHeaders);
    }

    public ControlPlaneHttpResponse runsCsv(
            Integer limit,
            Integer offset,
            String status,
            Boolean auditComplete
    ) throws IOException {
        return runsCsv(limit, offset, status, auditComplete, null);
    }

    public ControlPlaneHttpResponse runsCsv(
            Integer limit,
            Integer offset,
            String status,
            Boolean auditComplete,
            Map<String, String> requestHeaders
    ) throws IOException {
        return get("/runs/export.csv" + runQuery(limit, offset, status, auditComplete),
                "text/csv", requestHeaders);
    }

    public ControlPlaneHttpResponse traceCsv(String runId) throws IOException {
        return traceCsv(runId, null);
    }

    public ControlPlaneHttpResponse traceCsv(String runId, Map<String, String> requestHeaders) throws IOException {
        return get("/runs/" + encodePathSegment(requireText(runId, "runId")) + "/trace/export.csv",
                "text/csv", requestHeaders);
    }

    public ControlPlaneHttpResponse traceJsonl(String runId) throws IOException {
        return traceJsonl(runId, null);
    }

    public ControlPlaneHttpResponse traceJsonl(String runId, Map<String, String> requestHeaders) throws IOException {
        return get("/runs/" + encodePathSegment(requireText(runId, "runId")) + "/trace/export.jsonl",
                "application/x-ndjson", requestHeaders);
    }

    public ControlPlaneHttpResponse get(String path) throws IOException {
        return get(path, null);
    }

    public ControlPlaneHttpResponse get(String path, Map<String, String> requestHeaders) throws IOException {
        return get(path, "application/json", requestHeaders);
    }

    private ControlPlaneHttpResponse get(String path, String accept, Map<String, String> requestHeaders)
            throws IOException {
        String requestPath = path == null ? "" : path;
        if (!requestPath.isEmpty() && !requestPath.startsWith("/")) {
            requestPath = "/" + requestPath;
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(consoleApiBaseUrl + requestPath).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            applyHeaders(connection, defaultHeaders);
            applyHeaders(connection, requestHeaders);
            connection.setRequestProperty("Accept", requireText(accept, "accept"));
            if (!isBlank(sharedSecret)) {
                connection.setRequestProperty(tokenHeader, sharedSecret);
            }

            int statusCode = connection.getResponseCode();
            String body = readBody(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
            return new ControlPlaneHttpResponse(statusCode, body);
        } finally {
            connection.disconnect();
        }
    }

    private static String runQuery(Integer limit, Integer offset, String status, Boolean auditComplete)
            throws IOException {
        StringBuilder query = new StringBuilder();
        appendIntParam(query, "limit", limit, true);
        appendIntParam(query, "offset", offset, false);
        if (status != null) {
            appendParam(query, "status", requireText(status, "status"));
        }
        if (auditComplete != null) {
            appendParam(query, "auditComplete", auditComplete.toString());
        }
        return query.toString();
    }

    private static void appendIntParam(StringBuilder query, String name, Integer value, boolean positive)
            throws IOException {
        if (value == null) {
            return;
        }
        if (positive && value.intValue() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        if (!positive && value.intValue() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        appendParam(query, name, value.toString());
    }

    private static void appendParam(StringBuilder query, String name, String value) throws IOException {
        query.append(query.length() == 0 ? '?' : '&');
        query.append(encodeQueryParam(name)).append('=').append(encodeQueryParam(value));
    }

    private static void applyHeaders(HttpURLConnection connection, Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(
                    requireText(entry.getKey(), "request header name"),
                    entry.getValue() == null ? "" : entry.getValue());
        }
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] bytes = new byte[4096];
        int read;
        try {
            while ((read = stream.read(bytes)) != -1) {
                buffer.write(bytes, 0, read);
            }
        } finally {
            stream.close();
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String normalizeBaseUrl(String value) {
        String baseUrl = requireText(value, "consoleApiBaseUrl");
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        try {
            new URL(baseUrl);
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("consoleApiBaseUrl must be a valid URL", ex);
        }
        return baseUrl;
    }

    private static String encodePathSegment(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private static String encodeQueryParam(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String requireText(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Builder {
        private final String consoleApiBaseUrl;
        private String tokenHeader = DEFAULT_CONSOLE_TOKEN_HEADER;
        private String sharedSecret = "";
        private int connectTimeoutMillis = 5000;
        private int readTimeoutMillis = 30000;
        private final Map<String, String> defaultHeaders = new TreeMap<String, String>();

        private Builder(String consoleApiBaseUrl) {
            this.consoleApiBaseUrl = consoleApiBaseUrl;
        }

        public Builder tokenHeader(String tokenHeader) {
            this.tokenHeader = tokenHeader;
            return this;
        }

        public Builder sharedSecret(String sharedSecret) {
            this.sharedSecret = sharedSecret;
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

        public Builder defaultHeader(String name, String value) {
            this.defaultHeaders.put(requireText(name, "default header name"), value == null ? "" : value);
            return this;
        }

        public Builder defaultHeaders(Map<String, String> headers) {
            if (headers == null) {
                return this;
            }
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                defaultHeader(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public ActionGraphConsoleHttpClient build() {
            return new ActionGraphConsoleHttpClient(this);
        }
    }
}
