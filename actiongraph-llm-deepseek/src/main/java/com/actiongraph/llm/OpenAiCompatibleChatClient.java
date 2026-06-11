package com.actiongraph.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class OpenAiCompatibleChatClient extends AbstractHttpChatClient {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    private final String endpoint;
    private final String model;
    private final Map<String, String> extraHeaders;

    public OpenAiCompatibleChatClient(
            String endpoint,
            String model,
            String apiKey,
            Map<String, String> extraHeaders,
            Duration timeout
    ) {
        this(new OkHttpClient.Builder()
                        .callTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                        .readTimeout((timeout == null ? DEFAULT_TIMEOUT : timeout).toMillis(), TimeUnit.MILLISECONDS)
                        .build(),
                new ObjectMapper(),
                endpoint,
                model,
                apiKey,
                extraHeaders);
    }

    public OpenAiCompatibleChatClient(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String endpoint,
            String model,
            String apiKey,
            Map<String, String> extraHeaders
    ) {
        super(httpClient, objectMapper, apiKey);
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        this.endpoint = endpoint;
        this.model = model;
        this.extraHeaders = Map.copyOf(Objects.requireNonNull(extraHeaders, "extraHeaders"));
    }

    @Override
    protected HttpRequestSpec buildRequest(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())
        ));
        body.putAll(extraBodyProperties());
        body.put("response_format", Map.of("type", "json_object"));
        body.put("max_tokens", request.maxTokens());
        body.put("stream", false);

        try {
            return new HttpRequestSpec(endpoint, objectMapper().writeValueAsString(body), extraHeaders);
        } catch (Exception ex) {
            throw new LlmClientException("Could not serialize OpenAI-compatible request", ex);
        }
    }

    protected Map<String, Object> extraBodyProperties() {
        return Map.of();
    }

    @Override
    protected String parseContent(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper().readTree(responseBody);
        } catch (Exception ex) {
            throw new LlmClientException("Could not parse OpenAI-compatible response", ex);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new LlmClientException("OpenAI-compatible response did not include choices");
        }
        return choices.get(0).path("message").path("content").asText("");
    }
}
