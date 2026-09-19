package by.gusto.notification.service;

import by.gusto.auth.entity.User;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.notification.entity.NotificationSubscription;
import by.gusto.notification.repository.NotificationSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Подписка на Telegram (S32): «привязка по коду» — пользователь генерирует
 * одноразовый код (Redis, TTL 15 мин) и отправляет боту /start <КОД>;
 * вебхук бота связывает chat_id с пользователем.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final String CODE_PREFIX = "tg-code:";

    private final NotificationSubscriptionRepository subscriptionRepository;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    /** Одноразовый код привязки чата (действует 15 минут). */
    public String createLinkCode(User user) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        redisTemplate.opsForValue().set(CODE_PREFIX + code, user.getId().toString(), Duration.ofMinutes(15));
        return code;
    }

    /** Обработка /start <КОД> из вебхука: связывает chat_id с пользователем. */
    @Transactional
    public UUID linkByCode(String code, String chatId) {
        String key = CODE_PREFIX + code;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Код не найден или истёк");
        }
        redisTemplate.delete(key);

        subscriptionRepository.findAllByUserIdAndChannelAndActiveTrue(UUID.fromString(userId),
                        NotificationSubscription.Channel.TELEGRAM).stream()
                .filter(s -> s.getDestination().equals(chatId))
                .findFirst()
                .ifPresentOrElse(s -> {
                    // уже подписан — ничего не меняем
                }, () -> subscriptionRepository.save(NotificationSubscription.builder()
                        .userId(UUID.fromString(userId))
                        .channel(NotificationSubscription.Channel.TELEGRAM)
                        .destination(chatId)
                        .build()));
        return UUID.fromString(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationSubscription> mySubscriptions(User user) {
        return subscriptionRepository.findAllByUserId(user.getId());
    }

    @Transactional
    public void unsubscribe(User user, UUID subscriptionId) {
        NotificationSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Подписка не найдена"));
        if (!subscription.getUserId().equals(user.getId())) {
            throw new GustoException(ErrorCode.ACCESS_DENIED);
        }
        subscription.setActive(false);
        subscriptionRepository.save(subscription);
    }
}
