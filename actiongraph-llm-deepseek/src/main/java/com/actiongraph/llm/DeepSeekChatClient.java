package com.actiongraph.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class DeepSeekChatClient implements LlmClient {
    public static final String DEFAULT_ENDPOINT = "https://api.deepseek.com/chat/completions";
    public static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;
    private final String model;

    public DeepSeekChatClient(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String endpoint,
            String apiKey,
            String model
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient must not be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
    }

    public static Optional<DeepSeekChatClient> fromEnvironment() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        String model = System.getenv().getOrDefault("DEEPSEEK_MODEL", DEFAULT_MODEL);
        String endpoint = System.getenv().getOrDefault("DEEPSEEK_BASE_URL", DEFAULT_ENDPOINT);
        return Optional.of(new DeepSeekChatClient(
                new OkHttpClient.Builder()
                        .callTimeout(Duration.ofSeconds(20))
                        .readTimeout(20, TimeUnit.SECONDS)
                        .build(),
                new ObjectMapper(),
                endpoint,
                apiKey,
                model
        ));
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.userPrompt())
                ),
                "thinking", Map.of("type", "disabled"),
                "response_format", Map.of("type", "json_object"),
                "max_tokens", request.maxTokens(),
                "stream", false
        );

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new LlmClientException("Could not serialize DeepSeek request", ex);
        }

        Request httpRequest = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new LlmClientException("DeepSeek request failed with HTTP " + response.code());
            }
            return new LlmResponse(extractMessageContent(responseBody));
        } catch (java.io.IOException ex) {
            throw new LlmClientException("DeepSeek request failed", ex);
        }
    }

    private String extractMessageContent(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new LlmClientException("Could not parse DeepSeek response", ex);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new LlmClientException("DeepSeek response did not include choices");
        }
        String text = choices.get(0).path("message").path("content").asText("");
        if (text.isBlank()) {
            throw new LlmClientException("DeepSeek response message content was empty");
        }
        return text;
    }
}
