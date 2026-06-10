package com.company.legacygateway;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public final class RawHttpActionGraphGatewayUsage {
    public static final String DEFAULT_RUNTIME_TOKEN_HEADER = "X-ActionGraph-Runtime-Token";

    private RawHttpActionGraphGatewayUsage() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> known = new HashMap<String, String>();
        known.put("customerId", "C001");

        LegacyHttpResponse response = start(
                requireEnvironment("ACTIONGRAPH_RUNTIME_URL"),
                System.getenv("ACTIONGRAPH_RUNTIME_TOKEN"),
                "Prepare renewal quote for C001",
                known);
        if (!response.successful()) {
            throw new IOException("ActionGraph request failed: HTTP "
                    + response.statusCode() + " " + response.body());
        }
        System.out.println(response.body());
    }

    public static LegacyHttpResponse interpret(
            String runtimeApiBaseUrl,
            String sharedSecret,
            String input,
            Map<String, String> knownParameters
    ) throws IOException {
        return post(runtimeApiBaseUrl, sharedSecret, DEFAULT_RUNTIME_TOKEN_HEADER,
                "/interpret", goalRequestJson(input, knownParameters), 5000, 30000);
    }

    public static LegacyHttpResponse start(
            String runtimeApiBaseUrl,
            String sharedSecret,
            String input,
            Map<String, String> knownParameters
    ) throws IOException {
        return post(runtimeApiBaseUrl, sharedSecret, DEFAULT_RUNTIME_TOKEN_HEADER,
                "/runs", goalRequestJson(input, knownParameters), 5000, 30000);
    }

    public static LegacyHttpResponse resume(
            String runtimeApiBaseUrl,
            String sharedSecret,
            String runId
    ) throws IOException {
        return post(runtimeApiBaseUrl, sharedSecret, DEFAULT_RUNTIME_TOKEN_HEADER,
                "/runs/" + encodePathSegment(requireText(runId, "runId")) + "/resume",
                "{}", 5000, 30000);
    }

    public static LegacyHttpResponse post(
            String runtimeApiBaseUrl,
            String sharedSecret,
            String tokenHeader,
            String path,
            String jsonBody,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) throws IOException {
        String baseUrl = normalizeBaseUrl(runtimeApiBaseUrl);
        String requestPath = path == null ? "" : path;
        if (!requestPath.startsWith("/")) {
            requestPath = "/" + requestPath;
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + requestPath).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (!isBlank(sharedSecret)) {
                connection.setRequestProperty(requireText(tokenHeader, "tokenHeader"), sharedSecret);
            }
            connection.setDoOutput(true);

            byte[] payload = (jsonBody == null ? "" : jsonBody).getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(payload.length);
            OutputStream output = connection.getOutputStream();
            try {
                output.write(payload);
            } finally {
                output.close();
            }

            int statusCode = connection.getResponseCode();
            InputStream responseStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new LegacyHttpResponse(statusCode, readBody(responseStream));
        } finally {
            connection.disconnect();
        }
    }

    private static String goalRequestJson(String input, Map<String, String> knownParameters) {
        StringBuilder json = new StringBuilder();
        json.append("{\"input\":").append(jsonString(requireText(input, "input"))).append(",\"knownParameters\":{");
        Map<String, String> sorted = knownParameters == null
                ? new TreeMap<String, String>()
                : new TreeMap<String, String>(knownParameters);
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) {
                json.append(',');
            }
            json.append(jsonString(requireText(entry.getKey(), "known parameter key")))
                    .append(':')
                    .append(jsonString(entry.getValue() == null ? "" : entry.getValue()));
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
                        appendUnicodeEscape(escaped, ch);
                    } else {
                        escaped.append(ch);
                    }
                    break;
            }
        }
        escaped.append('"');
        return escaped.toString();
    }

    private static void appendUnicodeEscape(StringBuilder escaped, char ch) {
        escaped.append("\\u");
        String hex = Integer.toHexString(ch);
        for (int i = hex.length(); i < 4; i++) {
            escaped.append('0');
        }
        escaped.append(hex);
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
        return new String(buffer.toByteArray(), "UTF-8");
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

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (isBlank(value)) {
            throw new IllegalStateException(name + " must be configured");
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    public static final class LegacyHttpResponse {
        private final int statusCode;
        private final String body;

        public LegacyHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }

        public boolean successful() {
            return statusCode >= 200 && statusCode < 300;
        }

        public int statusCode() {
            return statusCode;
        }

        public String body() {
            return body;
        }
    }
}
