package by.gusto.notification.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Клиент Telegram Bot API (S32): только sendMessage; без токена интеграция
 * выключена (send() кидает исключение — канал решает, что делать).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramApiClient {

    private final TelegramProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public void sendMessage(String chatId, String text) {
        if (!isEnabled()) {
            throw new IllegalStateException("Telegram не настроен (TELEGRAM_BOT_TOKEN пуст)");
        }
        String response = RestClient.create()
                .post()
                .uri("https://api.telegram.org/bot{token}/sendMessage", properties.getBotToken())
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .body(String.class);
        log.debug("TELEGRAM sendMessage -> {}: {}", chatId, response);
    }
}
