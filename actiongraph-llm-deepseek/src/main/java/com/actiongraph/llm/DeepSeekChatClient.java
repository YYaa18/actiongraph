package com.actiongraph.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class DeepSeekChatClient extends OpenAiCompatibleChatClient {
    public static final String DEFAULT_ENDPOINT = "https://api.deepseek.com/chat/completions";
    public static final String DEFAULT_MODEL = "deepseek-v4-flash";

    public DeepSeekChatClient(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String endpoint,
            String apiKey,
            String model
    ) {
        super(httpClient, objectMapper, endpoint, model, apiKey, Map.of());
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
    protected Map<String, Object> extraBodyProperties() {
        return Map.of("thinking", Map.of("type", "disabled"));
    }
}
