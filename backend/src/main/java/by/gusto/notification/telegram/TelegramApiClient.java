package by.gusto.notification.telegram;

import by.gusto.common.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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

    public boolean isEnabled() {
        return !effectiveToken().isBlank();
    }

    public void sendMessage(String chatId, String text) {
        String token = effectiveToken();
        if (token.isBlank()) {
            throw new IllegalStateException("Telegram не настроен (токен пуст и в settings, и в .env)");
        }
        RestClient.create()
                .post()
                .uri("https://api.telegram.org/bot{token}/sendMessage", token)
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .body(String.class);
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
