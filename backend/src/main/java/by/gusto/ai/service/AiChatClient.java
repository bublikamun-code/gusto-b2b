package by.gusto.ai.service;

import by.gusto.ai.config.AiProperties;
import by.gusto.ai.dto.AiAdvisorDtos.ChatTurn;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

/**
 * Клиент OpenAI-совместимого chat-completions API (S45). Провайдер не зашит —
 * работает с любым endpoint, который говорит на этом протоколе.
 *
 * Класс не бросает исключений наружу: сетевой сбой, 5xx, невалидный ответ — это
 * «модель недоступна», и вызывающий переключается на детерминированного
 * советника. Лендинг не должен падать из-за чужого API.
 */
@Component
@Slf4j
public class AiChatClient {

    private final AiProperties properties;
    private final RestClient restClient;

    public AiChatClient(AiProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build());
        factory.setReadTimeout(properties.getTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    public boolean isAvailable() {
        return properties.isEnabled() && properties.isModelConfigured();
    }

    /**
     * @return текст ответа либо null, если модель недоступна или ответ пустой.
     */
    public String complete(String systemPrompt, List<ChatTurn> history, String userMessage) {
        if (!isAvailable()) {
            return null;
        }
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChatCompletionRequest(properties.getModel(), systemPrompt, history, userMessage))
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            String content = response == null || response.choices() == null || response.choices().isEmpty()
                    ? null
                    : response.choices().get(0).message().content();
            return content == null || content.isBlank() ? null : content.trim();
        } catch (Exception e) {
            log.warn("AI_ADVISOR модель {} недоступна: {}", properties.getModel(), e.getMessage());
            return null;
        }
    }

    // --- wire-формат OpenAI ------------------------------------------------------

    private record ChatCompletionRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature,
            List<Message> messages) {

        ChatCompletionRequest(String model, String systemPrompt, List<ChatTurn> history, String userMessage) {
            this(model, 900, 0.4, buildMessages(systemPrompt, history, userMessage));
        }

        private static List<Message> buildMessages(String systemPrompt, List<ChatTurn> history, String userMessage) {
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("system", systemPrompt));
            for (ChatTurn turn : history == null ? List.<ChatTurn>of() : history) {
                messages.add(new Message(turn.role(), turn.content()));
            }
            messages.add(new Message("user", userMessage));
            return messages;
        }
    }

    private record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(ChatMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatMessage(String content) {
    }
}
