package by.gusto.notification.controller;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.notification.entity.NotificationSubscription;
import by.gusto.notification.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Подписки на уведомления (S32): генерация одноразового кода привязки
 * Telegram-чата, список подписок, отписка.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final AuthContext authContext;

    @PostMapping("/telegram/code")
    public ResponseEntity<ApiResponse<Map<String, String>>> linkCode() {
        User user = authContext.getCurrentUser();
        String code = subscriptionService.createLinkCode(user);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "code", code,
                "instruction", "Отправьте боту команду /start " + code + " — действует 15 минут")));
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<List<SubscriptionItem>>> my() {
        User user = authContext.getCurrentUser();
        List<SubscriptionItem> items = subscriptionService.mySubscriptions(user).stream()
                .map(s -> new SubscriptionItem(s.getId(), s.getChannel().name(),
                        s.getDestination(), s.isActive()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @DeleteMapping("/subscriptions/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> unsubscribe(@PathVariable UUID id) {
        User user = authContext.getCurrentUser();
        subscriptionService.unsubscribe(user, id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Подписка отключена")));
    }

    public record SubscriptionItem(UUID id, String channel, String destination, boolean active) {
    }
}
