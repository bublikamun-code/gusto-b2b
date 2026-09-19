package by.gusto.notification.telegram;

import by.gusto.notification.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Вебхук Telegram (S32): апдейты бота. Защита — секрет в query/header
 * (стандарт X-Telegram-Bot-Api-Secret-Token). Обрабатывается /start <КОД>:
 * привязка chat_id к пользователю (подписка на уведомления).
 * Настройка вебхука в Bot API — см. docs/runbooks.md.
 */
@RestController
@RequestMapping("/api/v1/notifications/telegram")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final TelegramProperties properties;
    private final SubscriptionService subscriptionService;
    private final by.gusto.common.settings.SettingsService settingsService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> update,
            jakarta.servlet.http.HttpServletRequest request) {
        if (!properties.isWebhookConfigured() || !secretMatches(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false));
        }

        try {
            Map<String, Object> message = (Map<String, Object>) update.get("message");
            if (message != null) {
                String chatId = String.valueOf(((Map<?, ?>) message.get("chat")).get("id"));
                String text = String.valueOf(message.getOrDefault("text", ""));
                if (text.startsWith("/start")) {
                    String[] parts = text.split("\\s+");
                    if (parts.length == 2) {
                        subscriptionService.linkByCode(parts[1].trim(), chatId);
                        reply(properties, chatId, "Подписка на уведомления ГУСТО оформлена.");
                        return ResponseEntity.ok(Map.of("ok", true));
                    }
                    reply(properties, chatId,
                            "Отправьте /start с кодом из личного кабинета: /start 123456");
                    return ResponseEntity.ok(Map.of("ok", true));
                }
            }
        } catch (Exception e) {
            log.warn("TELEGRAM webhook: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private boolean secretMatches(HttpServletRequest request) {
        String header = request.getHeader("X-Telegram-Bot-Api-Secret-Token");
        String query = request.getParameter("secret");
        String provided = header != null ? header : query;
        return properties.getWebhookSecret().equals(provided);
    }

    private void reply(TelegramProperties properties, String chatId, String text) {
        try {
            new TelegramApiClient(properties, settingsService).sendMessage(chatId, text);
        } catch (Exception e) {
            log.warn("TELEGRAM ответ не отправлен: {}", e.getMessage());
        }
    }
}
