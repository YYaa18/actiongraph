package com.actiongraph.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractHttpChatClient implements LlmClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    protected AbstractHttpChatClient(OkHttpClient httpClient, ObjectMapper objectMapper, String apiKey) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        this.apiKey = apiKey;
    }

    @Override
    public final LlmResponse complete(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        HttpRequestSpec spec = Objects.requireNonNull(buildRequest(request), "request spec");
        if (spec.endpoint() == null || spec.endpoint().isBlank()) {
            throw new LlmClientException("LLM request endpoint must not be blank");
        }
        if (spec.body() == null || spec.body().isBlank()) {
            throw new LlmClientException("LLM request body must not be blank");
        }

        Map<String, String> headers = new LinkedHashMap<>(spec.headers());
        headers.putAll(authHeaders());

        Request.Builder builder = new Request.Builder()
                .url(spec.endpoint())
                .post(RequestBody.create(spec.body(), JSON));
        headers.forEach(builder::header);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new LlmClientException("LLM request failed with HTTP " + response.code());
            }
            String content = parseContent(responseBody);
            if (content == null || content.isBlank()) {
                throw new LlmClientException("LLM response message content was empty");
            }
            return new LlmResponse(content);
        } catch (java.io.IOException ex) {
            throw new LlmClientException("LLM request failed", ex);
        }
    }

    protected ObjectMapper objectMapper() {
        return objectMapper;
    }

    protected abstract HttpRequestSpec buildRequest(LlmRequest request);

    protected abstract String parseContent(String responseBody);

    protected Map<String, String> authHeaders() {
        return Map.of("Authorization", "Bearer " + apiKey);
    }

    protected record HttpRequestSpec(String endpoint, String body, Map<String, String> headers) {
        public HttpRequestSpec {
            headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
        }
    }
}
