package com.actiongraph.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekChatClientTest {
    @Test
    void sendsOpenAiCompatibleJsonRequestAndExtractsContent() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id": "cmpl-test",
                              "choices": [
                                {
                                  "finish_reason": "stop",
                                  "index": 0,
                                  "message": {
                                    "content": "{\\"goalType\\":\\"prepareRenewalQuote\\",\\"parameters\\":{\\"customerId\\":\\"C001\\"},\\"missingFields\\":[],\\"clarificationQuestion\\":null}",
                                    "role": "assistant"
                                  }
                                }
                              ]
                            }
                            """));
            server.start();

            ObjectMapper objectMapper = new ObjectMapper();
            DeepSeekChatClient client = new DeepSeekChatClient(
                    new OkHttpClient(),
                    objectMapper,
                    server.url("/chat/completions").toString(),
                    "test-key",
                    "deepseek-v4-flash"
            );

            LlmResponse response = client.complete(new LlmRequest("system", "user", 250));

            assertThat(response.text()).contains("\"goalType\":\"prepareRenewalQuote\"");
            var recorded = server.takeRequest();
            assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-key");
            assertThat(recorded.getPath()).isEqualTo("/chat/completions");

            JsonNode requestBody = objectMapper.readTree(recorded.getBody().readUtf8());
            assertThat(requestBody.path("model").asText()).isEqualTo("deepseek-v4-flash");
            assertThat(requestBody.path("response_format").path("type").asText()).isEqualTo("json_object");
            assertThat(requestBody.path("thinking").path("type").asText()).isEqualTo("disabled");
            assertThat(requestBody.path("messages")).hasSize(2);
        }
    }

    @Test
    void openAiCompatibleClientSupportsExtraHeadersAndMapsHttpErrors() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"choices":[{"message":{"content":"{\\"goalType\\":\\"demo\\"}"}}]}
                            """));
            server.enqueue(new MockResponse().setResponseCode(429).setBody("{}"));
            server.start();

            OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
                    server.url("/v1/chat/completions").toString(),
                    "qwen-max",
                    "test-key",
                    Map.of("X-Gateway-AppId", "actiongraph"),
                    Duration.ofSeconds(5)
            );

            assertThat(client.complete(new LlmRequest("system", "user", 250)).text())
                    .contains("\"goalType\":\"demo\"");

            var recorded = server.takeRequest();
            assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-key");
            assertThat(recorded.getHeader("X-Gateway-AppId")).isEqualTo("actiongraph");
            JsonNode requestBody = new ObjectMapper().readTree(recorded.getBody().readUtf8());
            assertThat(requestBody.path("model").asText()).isEqualTo("qwen-max");
            assertThat(requestBody.path("thinking").isMissingNode()).isTrue();

            assertThatThrownBy(() -> client.complete(new LlmRequest("system", "user", 250)))
                    .isInstanceOf(LlmClientException.class)
                    .hasMessageContaining("HTTP 429");
        }
    }
}
