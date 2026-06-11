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

public final class ActionGraphRuntimeHttpClient implements ActionGraphRuntimeGateway {
    public static final String DEFAULT_RUNTIME_TOKEN_HEADER = "X-ActionGraph-Runtime-Token";

    private final String runtimeApiBaseUrl;
    private final String tokenHeader;
    private final String sharedSecret;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final Map<String, String> defaultHeaders;

    private ActionGraphRuntimeHttpClient(Builder builder) {
        this.runtimeApiBaseUrl = normalizeBaseUrl(builder.runtimeApiBaseUrl);
        this.tokenHeader = requireText(builder.tokenHeader, "tokenHeader");
        this.sharedSecret = builder.sharedSecret == null ? "" : builder.sharedSecret;
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.readTimeoutMillis = builder.readTimeoutMillis;
        this.defaultHeaders = new TreeMap<String, String>(builder.defaultHeaders);
    }

    public static Builder builder(String runtimeApiBaseUrl) {
        return new Builder(runtimeApiBaseUrl);
    }

    @Override
    public ControlPlaneHttpResponse interpret(String input) throws IOException {
        return interpret(input, null);
    }

    @Override
    public ControlPlaneHttpResponse interpret(String input, Map<String, String> knownParameters) throws IOException {
        return interpret(input, knownParameters, null);
    }

    @Override
    public ControlPlaneHttpResponse interpret(
            String input,
            Map<String, String> knownParameters,
            Map<String, String> requestHeaders
    ) throws IOException {
        return post("/interpret", goalRequestJson(input, knownParameters), requestHeaders);
    }

    @Override
    public ControlPlaneHttpResponse start(String input) throws IOException {
        return start(input, null);
    }

    @Override
    public ControlPlaneHttpResponse start(String input, Map<String, String> knownParameters) throws IOException {
        return start(input, knownParameters, null);
    }

    @Override
    public ControlPlaneHttpResponse start(
            String input,
            Map<String, String> knownParameters,
            Map<String, String> requestHeaders
    ) throws IOException {
        return post("/runs", goalRequestJson(input, knownParameters), requestHeaders);
    }

    @Override
    public ControlPlaneHttpResponse resume(String runId) throws IOException {
        return resume(runId, null);
    }

    @Override
    public ControlPlaneHttpResponse resume(String runId, Map<String, String> requestHeaders) throws IOException {
        return post("/runs/" + encodePathSegment(requireText(runId, "runId")) + "/resume", "{}", requestHeaders);
    }

    public ControlPlaneHttpResponse post(String path, String jsonBody) throws IOException {
        return post(path, jsonBody, null);
    }

    public ControlPlaneHttpResponse post(String path, String jsonBody, Map<String, String> requestHeaders)
            throws IOException {
        String requestPath = path == null ? "" : path;
        if (!requestPath.startsWith("/")) {
            requestPath = "/" + requestPath;
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(runtimeApiBaseUrl + requestPath).openConnection();
        try {
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
            return new ControlPlaneHttpResponse(statusCode, body);
        } finally {
            connection.disconnect();
        }
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

    private static String goalRequestJson(String input, Map<String, String> knownParameters) {
        String safeInput = requireText(input, "input");
        StringBuilder json = new StringBuilder();
        json.append("{\"input\":").append(jsonString(safeInput)).append(",\"knownParameters\":{");
        Map<String, String> sorted = knownParameters == null
                ? new TreeMap<String, String>()
                : new TreeMap<String, String>(knownParameters);
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            String key = requireText(entry.getKey(), "known parameter key");
            if (!first) {
                json.append(',');
            }
            json.append(jsonString(key)).append(':').append(jsonString(entry.getValue() == null ? "" : entry.getValue()));
            first = false;
        }
        json.append("}}");
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
                        escaped.append(String.format("\\u%04x", (int) ch));
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

    private static String normalizeBaseUrl(String value) {
        String baseUrl = requireText(value, "runtimeApiBaseUrl");
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        try {
            new URL(baseUrl);
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("runtimeApiBaseUrl must be a valid URL", ex);
        }
        return baseUrl;
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
        private final String runtimeApiBaseUrl;
        private String tokenHeader = DEFAULT_RUNTIME_TOKEN_HEADER;
        private String sharedSecret = "";
        private int connectTimeoutMillis = 5000;
        private int readTimeoutMillis = 30000;
        private final Map<String, String> defaultHeaders = new TreeMap<String, String>();

        private Builder(String runtimeApiBaseUrl) {
            this.runtimeApiBaseUrl = runtimeApiBaseUrl;
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

        public ActionGraphRuntimeHttpClient build() {
            return new ActionGraphRuntimeHttpClient(this);
        }
    }
}
