package by.gusto.notification.telegram;

import by.gusto.common.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Клиент Telegram Bot API (S32): только sendMessage. Токен берётся из
 * настроек админки (S38, telegram.bot_token), если задан, иначе из .env
 * (S32). Токена нет нигде — интеграция выключена (send() кидает исключение —
 * канал решает, что делать).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramApiClient {

    private final TelegramProperties properties;
    private final SettingsService settingsService;

    /**
     * Таймаут сетевого вызова к Telegram. Раньше RestClient.create() шёл с бесконечными
     * таймаутами: зависший api.telegram.org навсегда занимал единственный поток @Scheduled
     * и останавливал outbox, очистку токенов и ротацию sequence вместе с email-каналом
     * (аудит 2026-09-30, P1-16).
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    public boolean isEnabled() {
        return !effectiveToken().isBlank();
    }

    public void sendMessage(String chatId, String text) {
        String token = effectiveToken();
        if (token.isBlank()) {
            throw new IllegalStateException("Telegram не настроен (токен пуст и в settings, и в .env)");
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        try {
            RestClient.builder()
                    .requestFactory(factory)
                    .build()
                    .post()
                    .uri("https://api.telegram.org/bot{token}/sendMessage", token)
                    .body(Map.of("chat_id", chatId, "text", text))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            // Токен лежит в пути URI, а текст сетевых исключений Spring содержит полный URI —
            // так он попадал в docker compose logs при любой сетевой ошибке (аудит, P1-17).
            // Отдаём наружу сообщение без токена: логируют именно e.getMessage().
            throw new IllegalStateException("Ошибка отправки в Telegram (chat " + chatId + "): "
                    + e.getClass().getSimpleName(), e);
        }
        log.debug("TELEGRAM sendMessage -> {} отправлено", chatId);
    }

    /** Настройка админки приоритетнее .env: смена токена не требует пересборки (S38). */
    private String effectiveToken() {
        return settingsService.getScalar(SettingsService.TELEGRAM_BOT_TOKEN)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElseGet(() -> properties.getBotToken() == null
                        ? "" : properties.getBotToken().trim());
    }
}
