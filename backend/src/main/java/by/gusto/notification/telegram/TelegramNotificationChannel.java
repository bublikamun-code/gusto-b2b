package by.gusto.notification.telegram;

import by.gusto.auth.entity.Role;
import by.gusto.auth.repository.UserRepository;
import by.gusto.company.repository.CompanyRepository;
import by.gusto.notification.entity.NotificationSubscription;
import by.gusto.notification.repository.NotificationSubscriptionRepository;
import by.gusto.order.repository.OrderRepository;
import by.gusto.outbox.channel.OutboxChannel;
import by.gusto.outbox.entity.OutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Канал Telegram-уведомлений (S32): новый заказ, смена статуса, счёт,
 * заявка с сайта (2.6). Когда интеграция не настроена (пустой токен),
 * сообщения пропускаются без ошибок — рассылка включается на запуске.
 * Сбой реальной отправки — исключение → ретрай поллера S31.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramNotificationChannel implements OutboxChannel {

    private final NotificationSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final OrderRepository orderRepository;
    private final TelegramApiClient telegramApiClient;

    @Override
    public boolean supports(String type) {
        return type.equals("ORDER_CREATED") || type.equals("ORDER_STATUS_CHANGED")
                || type.equals("INVOICE_ISSUED") || type.equals("SITE_REQUEST_CREATED");
    }

    @Override
    public boolean send(OutboxMessage message) {
        if (!telegramApiClient.isEnabled()) {
            log.info("TELEGRAM выключен: событие {} пропущено", message.getType());
            return true;
        }
        String text = template(message.getType(), message.getPayload());
        for (String chatId : resolveChatIds(message.getType(), message.getAggregateId(), message.getPayload())) {
            telegramApiClient.sendMessage(chatId, text);
        }
        return true;
    }

    // ----- адресаты и шаблоны (2.6) ----------------------------------------------

    /** Видимость пакетом для тестов: кому отправляем по типу события. */
    public List<String> resolveChatIds(String type, UUID aggregateId, Map<String, Object> payload) {
        List<NotificationSubscription> subscriptions = switch (type) {
            case "ORDER_CREATED" -> isRetail(payload)
                    ? subscriptionRepository.findActiveByRole(Role.MANAGER)
                    : subscriptionRepository.findActiveByRole(Role.MANAGER).stream()
                            .filter(s -> managerOfCompany(companyId(payload))
                                    .map(managerId -> managerId.equals(s.getUserId()))
                                    .orElse(false))
                            .toList();
            case "SITE_REQUEST_CREATED" -> subscriptionRepository.findActiveByRole(Role.MANAGER);
            case "ORDER_STATUS_CHANGED" -> orderRepository.findById(aggregateId)
                    .map(order -> subscriptionRepository
                            .findAllByUserIdAndChannelAndActiveTrue(order.getCustomerUserId(),
                                    NotificationSubscription.Channel.TELEGRAM))
                    .orElse(List.of());
            case "INVOICE_ISSUED" -> {
                UUID companyId = companyId(payload);
                yield companyId != null
                        ? subscriptionRepository.findActiveByCompanyId(companyId)
                        : List.of();
            }
            default -> List.of();
        };
        return subscriptions.stream().map(NotificationSubscription::getDestination).toList();
    }

    private String template(String type, Map<String, Object> payload) {
        return switch (type) {
            case "ORDER_CREATED" -> isRetail(payload)
                    ? "\uD83E\uDD69 Новый розничный заказ " + payload.get("number")
                            + " на " + payload.get("totalAmount") + " BYN (пул «не назначено»)"
                    : "\uD83E\uDD69 Новый заказ " + payload.get("number")
                            + " на " + payload.get("totalAmount") + " BYN";
            case "ORDER_STATUS_CHANGED" -> "Заказ " + payload.get("number")
                    + ": " + payload.get("from") + " → " + payload.get("to");
            case "INVOICE_ISSUED" -> "Выставлен счёт " + payload.get("number")
                    + " на " + payload.get("totalAmount") + " BYN";
            case "SITE_REQUEST_CREATED" -> "Новая заявка с сайта: " + payload.get("name")
                    + " (" + payload.get("type") + ")";
            default -> "Событие " + type;
        };
    }

    private boolean isRetail(Map<String, Object> payload) {
        return Boolean.parseBoolean(String.valueOf(payload.getOrDefault("retail", "false")));
    }

    private UUID companyId(Map<String, Object> payload) {
        Object value = payload.get("companyId");
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private java.util.Optional<UUID> managerOfCompany(UUID companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        return companyRepository.findById(companyId)
                .map(company -> company.getManagerId() != null
                        ? Optional.of(company.getManagerId()) : Optional.<UUID>empty())
                .orElse(Optional.empty());
    }
}
