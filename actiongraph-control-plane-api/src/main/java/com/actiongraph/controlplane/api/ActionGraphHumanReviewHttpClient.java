package com.actiongraph.controlplane.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public final class ActionGraphHumanReviewHttpClient {
    public static final String DEFAULT_REVIEW_TOKEN_HEADER = "X-ActionGraph-Review-Token";

    private final String taskApiBaseUrl;
    private final String callbackApiBaseUrl;
    private final String tokenHeader;
    private final String sharedSecret;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final Map<String, String> defaultHeaders;

    private ActionGraphHumanReviewHttpClient(Builder builder) {
        this.taskApiBaseUrl = normalizeBaseUrl(builder.taskApiBaseUrl, "taskApiBaseUrl");
        this.callbackApiBaseUrl = normalizeBaseUrl(
                builder.callbackApiBaseUrl == null
                        ? defaultCallbackApiBaseUrl(this.taskApiBaseUrl)
                        : builder.callbackApiBaseUrl,
                "callbackApiBaseUrl");
        this.tokenHeader = requireText(builder.tokenHeader, "tokenHeader");
        this.sharedSecret = builder.sharedSecret == null ? "" : builder.sharedSecret;
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.readTimeoutMillis = builder.readTimeoutMillis;
        this.defaultHeaders = new TreeMap<String, String>(builder.defaultHeaders);
    }

    public static Builder builder(String taskApiBaseUrl) {
        return new Builder(taskApiBaseUrl);
    }

    public ControlPlaneHttpResponse pendingTasks() throws IOException {
        return pendingTasks(null);
    }

    public ControlPlaneHttpResponse pendingTasks(Map<String, String> requestHeaders) throws IOException {
        return get("/pending", requestHeaders);
    }

    public ControlPlaneHttpResponse tasksForRun(String runId) throws IOException {
        return tasksForRun(runId, null);
    }

    public ControlPlaneHttpResponse tasksForRun(String runId, Map<String, String> requestHeaders) throws IOException {
        return get("/runs/" + encodePathSegment(requireText(runId, "runId")), requestHeaders);
    }

    public ControlPlaneHttpResponse task(String runId, String actionId) throws IOException {
        return task(runId, actionId, null);
    }

    public ControlPlaneHttpResponse task(String runId, String actionId, Map<String, String> requestHeaders)
            throws IOException {
        return get("/runs/" + encodePathSegment(requireText(runId, "runId"))
                + "/actions/" + encodePathSegment(requireText(actionId, "actionId")), requestHeaders);
    }

    public ControlPlaneHttpResponse decide(
            String runId,
            String actionId,
            Integer expectedStageIndex,
            String decision,
            String reviewer,
            String comment
    ) throws IOException {
        return decide(runId, actionId, expectedStageIndex, decision, reviewer, comment, null);
    }

    public ControlPlaneHttpResponse decide(
            String runId,
            String actionId,
            Integer expectedStageIndex,
            String decision,
            String reviewer,
            String comment,
            Map<String, String> requestHeaders
    ) throws IOException {
        return post(taskApiBaseUrl,
                "/runs/" + encodePathSegment(requireText(runId, "runId"))
                        + "/actions/" + encodePathSegment(requireText(actionId, "actionId"))
                        + "/decision",
                decisionJson(expectedStageIndex, decision, reviewer, comment),
                requestHeaders);
    }

    public ControlPlaneHttpResponse callback(
            String runId,
            String actionId,
            int expectedStageIndex,
            String decision,
            String reviewer,
            String comment
    ) throws IOException {
        return callback(runId, actionId, expectedStageIndex, decision, reviewer, comment, null);
    }

    public ControlPlaneHttpResponse callback(
            String runId,
            String actionId,
            int expectedStageIndex,
            String decision,
            String reviewer,
            String comment,
            Map<String, String> requestHeaders
    ) throws IOException {
        return post(callbackApiBaseUrl, "", callbackJson(
                runId, actionId, expectedStageIndex, decision, reviewer, comment), requestHeaders);
    }

    public ControlPlaneHttpResponse get(String path) throws IOException {
        return get(path, null);
    }

    public ControlPlaneHttpResponse get(String path, Map<String, String> requestHeaders) throws IOException {
        String requestPath = path == null ? "" : path;
        if (!requestPath.isEmpty() && !requestPath.startsWith("/")) {
            requestPath = "/" + requestPath;
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(taskApiBaseUrl + requestPath).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        applyHeaders(connection, defaultHeaders);
        applyHeaders(connection, requestHeaders);
        connection.setRequestProperty("Accept", "application/json");
        if (!isBlank(sharedSecret)) {
            connection.setRequestProperty(tokenHeader, sharedSecret);
        }

        int statusCode = connection.getResponseCode();
        String body = readBody(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        return new ControlPlaneHttpResponse(statusCode, body);
    }

    public ControlPlaneHttpResponse post(String path, String jsonBody) throws IOException {
        return post(taskApiBaseUrl, path, jsonBody, null);
    }

    public ControlPlaneHttpResponse post(String path, String jsonBody, Map<String, String> requestHeaders)
            throws IOException {
        return post(taskApiBaseUrl, path, jsonBody, requestHeaders);
    }

    private ControlPlaneHttpResponse post(
            String baseUrl,
            String path,
            String jsonBody,
            Map<String, String> requestHeaders
    ) throws IOException {
        String requestPath = path == null ? "" : path;
        if (!requestPath.isEmpty() && !requestPath.startsWith("/")) {
            requestPath = "/" + requestPath;
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + requestPath).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        applyHeaders(connection, defaultHeaders);
        applyHeaders(connection, requestHeaders);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        if (!isBlank(sharedSecret)) {
            connection.setRequestProperty(tokenHeader, sharedSecret);
        }
        connection.setDoOutput(true);
        byte[] payload = (jsonBody == null ? "" : jsonBody).getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(payload.length);
        OutputStream output = connection.getOutputStream();
        try {
            output.write(payload);
        } finally {
            output.close();
        }

        int statusCode = connection.getResponseCode();
        String body = readBody(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        return new ControlPlaneHttpResponse(statusCode, body);
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

    private static String decisionJson(Integer expectedStageIndex, String decision, String reviewer, String comment) {
        StringBuilder json = new StringBuilder();
        json.append("{\"expectedStageIndex\":");
        if (expectedStageIndex == null) {
            json.append("null");
        } else {
            json.append(expectedStageIndex.intValue());
        }
        json.append(",\"decision\":").append(jsonString(requireText(decision, "decision")));
        json.append(",\"reviewer\":").append(jsonString(reviewer == null ? "" : reviewer));
        json.append(",\"comment\":").append(jsonString(comment == null ? "" : comment));
        json.append('}');
        return json.toString();
    }

    private static String callbackJson(
            String runId,
            String actionId,
            int expectedStageIndex,
            String decision,
            String reviewer,
            String comment
    ) {
        StringBuilder json = new StringBuilder();
        json.append("{\"runId\":").append(jsonString(requireText(runId, "runId")));
        json.append(",\"actionId\":").append(jsonString(requireText(actionId, "actionId")));
        json.append(",\"expectedStageIndex\":").append(expectedStageIndex);
        json.append(",\"decision\":").append(jsonString(requireText(decision, "decision")));
        json.append(",\"reviewer\":").append(jsonString(reviewer == null ? "" : reviewer));
        json.append(",\"comment\":").append(jsonString(comment == null ? "" : comment));
        json.append('}');
        return json.toString();
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder();
        escaped.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", Integer.valueOf(ch)));
                    } else {
                        escaped.append(ch);
                    }
                    break;
            }
        }
        escaped.append('"');
        return escaped.toString();
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

    private static String normalizeBaseUrl(String value, String name) {
        String baseUrl = requireText(value, name);
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        try {
            new URL(baseUrl);
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(name + " must be a valid URL", ex);
        }
        return baseUrl;
    }

    private static String defaultCallbackApiBaseUrl(String taskApiBaseUrl) {
        if (taskApiBaseUrl.endsWith("/tasks")) {
            return taskApiBaseUrl.substring(0, taskApiBaseUrl.length() - "/tasks".length()) + "/callbacks";
        }
        return taskApiBaseUrl + "/callbacks";
    }

    private static String encodePathSegment(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
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
        private final String taskApiBaseUrl;
        private String callbackApiBaseUrl;
        private String tokenHeader = DEFAULT_REVIEW_TOKEN_HEADER;
        private String sharedSecret = "";
        private int connectTimeoutMillis = 5000;
        private int readTimeoutMillis = 30000;
        private final Map<String, String> defaultHeaders = new TreeMap<String, String>();

        private Builder(String taskApiBaseUrl) {
            this.taskApiBaseUrl = taskApiBaseUrl;
        }

        public Builder callbackApiBaseUrl(String callbackApiBaseUrl) {
            this.callbackApiBaseUrl = callbackApiBaseUrl;
            return this;
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

        public ActionGraphHumanReviewHttpClient build() {
            return new ActionGraphHumanReviewHttpClient(this);
        }
    }
}
