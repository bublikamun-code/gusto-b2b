package by.gusto.notification.email;

import by.gusto.auth.repository.UserRepository;
import by.gusto.notification.entity.NotificationSubscription;
import by.gusto.notification.repository.NotificationSubscriptionRepository;
import by.gusto.order.repository.OrderRepository;
import by.gusto.outbox.channel.OutboxChannel;
import by.gusto.outbox.entity.OutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Канал Email (S33): подтверждение email, сброс пароля, статус заказа и счёт —
 * клиенту по правилу 2.6 «только если нет Telegram-подписки». Ссылки — от
 * APP_BASE_URL. SMTP не настроен — события пропускаются; сбой — ретрай S31.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationChannel implements OutboxChannel {

    private final EmailSender emailSender;
    private final EmailTemplateRenderer templates;
    private final NotificationSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Override
    public boolean supports(String type) {
        return type.startsWith("EMAIL_")
                || type.equals("ORDER_STATUS_CHANGED")
                || type.equals("INVOICE_ISSUED");
    }

    @Override
    public boolean send(OutboxMessage message) {
        if (!emailSender.isEnabled()) {
            log.info("EMAIL выключен: событие {} пропущено", message.getType());
            return true;
        }
        switch (message.getType()) {
            case "EMAIL_CONFIRMATION" -> {
                String to = text(message.getPayload(), "to");
                String url = text(message.getPayload(), "confirmationUrl");
                emailSender.sendHtml(to, text(message.getPayload(), "subject"),
                        templates.render("Подтвердите email",
                                "Подтвердите email, чтобы завершить регистрацию в ГУСТО.", url,
                                "Подтвердить email"));
            }
            case "EMAIL_PASSWORD_RESET" -> {
                String to = text(message.getPayload(), "to");
                String url = text(message.getPayload(), "resetUrl");
                emailSender.sendHtml(to, text(message.getPayload(), "subject"),
                        templates.render("Восстановление пароля",
                                "Вы запросили восстановление пароля. Ссылка действует ограниченное время; "
                                        + "если это были не вы — просто проигнорируйте письмо.",
                                url, "Задать новый пароль"));
            }
            case "ORDER_STATUS_CHANGED" -> orderRepository.findById(message.getAggregateId())
                    .ifPresent(order -> {
                        String html = templates.render("Статус заказа " + text(message.getPayload(), "number"),
                                "Статус заказа " + text(message.getPayload(), "number") + ": <b>"
                                        + text(message.getPayload(), "from") + " → " + text(message.getPayload(), "to")
                                        + "</b>.", appBaseUrl() + "/cabinet/orders", "Мои заказы");
                        for (String to : resolveClientEmails(message.getType(), order.getCustomerUserId())) {
                            emailSender.sendHtml(to, "ГУСТО: статус заказа", html);
                        }
                    });
            case "INVOICE_ISSUED" -> {
                UUID companyId = companyId(message.getPayload());
                if (companyId != null) {
                    String html = templates.render("Выставлен счёт " + text(message.getPayload(), "number"),
                            "Счёт <b>" + text(message.getPayload(), "number") + "</b> на сумму "
                                    + text(message.getPayload(), "totalAmount") + " BYN ждёт в кабинете.",
                            appBaseUrl() + "/cabinet/documents", "Документы");
                    for (String to : resolveCompanyEmailsWithoutTelegram(companyId)) {
                        emailSender.sendHtml(to, "ГУСТО: счёт на оплату", html);
                    }
                }
            }
            default -> log.warn("EMAIL: необработанный тип {}", message.getType());
        }
        return true;
    }

    // ----- получатели ------------------------------------------------------------

    /**
     * Правило 2.6: клиенту письмо — только если у него НЕТ активной Telegram-подписки.
     * Пакетная видимость для тестов.
     */
    public List<String> resolveClientEmails(String type, UUID userId) {
        boolean hasTelegram = !subscriptionRepository
                .findAllByUserIdAndChannelAndActiveTrue(userId, NotificationSubscription.Channel.TELEGRAM)
                .isEmpty();
        if (hasTelegram) {
            return List.of();
        }
        return userRepository.findById(userId)
                .map(u -> List.of(u.getEmail()))
                .orElse(List.of());
    }

    public List<String> resolveCompanyEmailsWithoutTelegram(UUID companyId) {
        return userRepository.findAllByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .filter(user -> subscriptionRepository
                        .findAllByUserIdAndChannelAndActiveTrue(user.getId(),
                                NotificationSubscription.Channel.TELEGRAM)
                        .isEmpty())
                .map(user -> user.getEmail())
                .toList();
    }

    private String appBaseUrl() {
        return appBaseUrl;
    }

    private String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private UUID companyId(Map<String, Object> payload) {
        Object value = payload.get("companyId");
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return UUID.fromString(String.valueOf(value));
    }
}
