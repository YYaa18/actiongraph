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

public final class ActionGraphComponentCatalogHttpClient {
    public static final String DEFAULT_CATALOG_TOKEN_HEADER = "X-ActionGraph-Catalog-Token";

    private final String catalogApiBaseUrl;
    private final String tokenHeader;
    private final String sharedSecret;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final Map<String, String> defaultHeaders;

    private ActionGraphComponentCatalogHttpClient(Builder builder) {
        this.catalogApiBaseUrl = normalizeBaseUrl(builder.catalogApiBaseUrl);
        this.tokenHeader = requireText(builder.tokenHeader, "tokenHeader");
        this.sharedSecret = builder.sharedSecret == null ? "" : builder.sharedSecret;
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.readTimeoutMillis = builder.readTimeoutMillis;
        this.defaultHeaders = new TreeMap<String, String>(builder.defaultHeaders);
    }

    public static Builder builder(String catalogApiBaseUrl) {
        return new Builder(catalogApiBaseUrl);
    }

    public ControlPlaneHttpResponse catalog() throws IOException {
        return catalog(null);
    }

    public ControlPlaneHttpResponse catalog(Map<String, String> requestHeaders) throws IOException {
        return get("", requestHeaders);
    }

    public ControlPlaneHttpResponse modules() throws IOException {
        return modules(null);
    }

    public ControlPlaneHttpResponse modules(Map<String, String> requestHeaders) throws IOException {
        return get("/modules", requestHeaders);
    }

    public ControlPlaneHttpResponse modulesByCompatibility(String compatibility) throws IOException {
        return modulesByCompatibility(compatibility, null);
    }

    public ControlPlaneHttpResponse modulesByCompatibility(String compatibility, Map<String, String> requestHeaders)
            throws IOException {
        return get("/compatibility/" + encodePathSegment(requireText(compatibility, "compatibility")),
                requestHeaders);
    }

    public ControlPlaneHttpResponse module(String module) throws IOException {
        return module(module, null);
    }

    public ControlPlaneHttpResponse module(String module, Map<String, String> requestHeaders) throws IOException {
        return get("/modules/" + encodePathSegment(requireText(module, "module")), requestHeaders);
    }

    public ControlPlaneHttpResponse profiles() throws IOException {
        return profiles(null);
    }

    public ControlPlaneHttpResponse profiles(Map<String, String> requestHeaders) throws IOException {
        return get("/profiles", requestHeaders);
    }

    public ControlPlaneHttpResponse profile(String profile) throws IOException {
        return profile(profile, null);
    }

    public ControlPlaneHttpResponse profile(String profile, Map<String, String> requestHeaders) throws IOException {
        return get("/profiles/" + encodePathSegment(requireText(profile, "profile")), requestHeaders);
    }

    public ControlPlaneHttpResponse get(String path) throws IOException {
        return get(path, null);
    }

    public ControlPlaneHttpResponse get(String path, Map<String, String> requestHeaders) throws IOException {
        String requestPath = path == null ? "" : path;
        if (!requestPath.isEmpty() && !requestPath.startsWith("/")) {
            requestPath = "/" + requestPath;
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(catalogApiBaseUrl + requestPath).openConnection();
        try {
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
        String baseUrl = requireText(value, "catalogApiBaseUrl");
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        try {
            new URL(baseUrl);
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("catalogApiBaseUrl must be a valid URL", ex);
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
        private final String catalogApiBaseUrl;
        private String tokenHeader = DEFAULT_CATALOG_TOKEN_HEADER;
        private String sharedSecret = "";
        private int connectTimeoutMillis = 5000;
        private int readTimeoutMillis = 30000;
        private final Map<String, String> defaultHeaders = new TreeMap<String, String>();

        private Builder(String catalogApiBaseUrl) {
            this.catalogApiBaseUrl = catalogApiBaseUrl;
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

        public ActionGraphComponentCatalogHttpClient build() {
            return new ActionGraphComponentCatalogHttpClient(this);
        }
    }
}
