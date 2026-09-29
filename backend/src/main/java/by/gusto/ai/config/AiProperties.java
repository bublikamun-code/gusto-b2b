package by.gusto.ai.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Настройки ИИ-советника (S45). Провайдер не зашит: любой OpenAI-совместимый
 * endpoint (OpenAI, DeepSeek, YandexGPT через прокси, локальный Ollama) —
 * достаточно задать base-url, api-key и model.
 *
 * Пустой api-key — не поломка: блок рецептов работает всегда, а чат отвечает
 * детерминированным советником по контенту рецептов (AiAdvisorService#fallbackReply).
 * Так витрина не остаётся пустой на стенде, где ключа нет.
 */
@Component
@Data
@Slf4j
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Выключает чат целиком: рецепты остаются, чат отдаёт 404. */
    private boolean enabled = true;

    /** Корень OpenAI-совместимого API, без /v1 — например https://api.openai.com. */
    private String baseUrl = "https://api.openai.com";

    private String apiKey = "";

    private String model = "gpt-4o-mini";

    /** Таймауты запроса к модели: connect + read, оба жёстко ограничены. */
    private Duration timeout = Duration.ofSeconds(20);

    /** Сколько последних реплик диалога уходит в модель вместе с текущей. */
    private int historyLimit = 8;

    /** Ограничение длины реплики пользователя на входе (символы). */
    private int maxMessageLength = 1000;

    /** Rate limit чата с одного IP, запросов в окне. */
    private int requestsPerHour = 20;

    public boolean isModelConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Мусор в конфиге (0/отрицательные лимиты) не должен ни ронять старт, ни
     * ломать рантайм (substring с отрицательной границей, постоянный 429):
     * невозможное значение заменяем дефолтом с предупреждением в лог.
     */
    @PostConstruct
    void sanitizeMisconfig() {
        historyLimit = positiveOrDefault(historyLimit, 8, "app.ai.history-limit");
        maxMessageLength = positiveOrDefault(maxMessageLength, 1000, "app.ai.max-message-length");
        requestsPerHour = positiveOrDefault(requestsPerHour, 20, "app.ai.requests-per-hour");
    }

    private int positiveOrDefault(int value, int fallback, String name) {
        if (value > 0) {
            return value;
        }
        log.warn("AI_ADVISOR некорректное значение {} = {}, используем {}", name, value, fallback);
        return fallback;
    }
}
