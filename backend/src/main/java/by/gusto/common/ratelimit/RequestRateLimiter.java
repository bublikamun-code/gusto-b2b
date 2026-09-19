package by.gusto.common.ratelimit;

import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Rate limiting произвольных эндпоинтов (S40) поверх Redis. Публичная форма
 * заявок и auth-эндпоинты лимитируются своими механизмами (S31/S08); здесь —
 * общий примитив для авторизованных операций: экспорт/импорт 1С 10/час на
 * пользователя (матрица 2.1), файловые операции и т.п.
 */
@Service
@RequiredArgsConstructor
public class RequestRateLimiter {

    private final StringRedisTemplate redisTemplate;

    /**
     * Учитывает обращение и бросает RATE_LIMITED, если лимит исчерпан.
     * Окно фиксированное: счётчик живёт window от первого обращения.
     */
    public void enforcePerUser(java.util.UUID userId, String endpoint, int limit, Duration window) {
        enforceIdentity(endpoint, "user:" + (userId == null ? "unknown" : userId), limit, window);
    }

    public void enforcePerIp(String ip, String endpoint, int limit, Duration window) {
        enforceIdentity(endpoint, "ip:" + (ip == null ? "unknown" : ip), limit, window);
    }

    private void enforceIdentity(String endpoint, String identity, int limit, Duration window) {
        String key = "rate:api:" + endpoint + ":" + identity;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1) {
            redisTemplate.opsForValue().set(key, "1", window);
        }
        if (current != null && current > limit) {
            throw new GustoException(ErrorCode.RATE_LIMITED);
        }
    }
}
