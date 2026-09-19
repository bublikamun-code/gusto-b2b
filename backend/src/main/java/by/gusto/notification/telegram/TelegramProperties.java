package by.gusto.notification.telegram;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки Telegram (S32): токен бота и секрет вебхука из .env/compose.
 * Пустой токен — интеграция выключена: канал пропускает события без ошибок.
 */
@Component
@Data
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    private String botToken = "";

    private String webhookSecret = "";

    public boolean isEnabled() {
        return botToken != null && !botToken.isBlank();
    }

    public boolean isWebhookConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}
