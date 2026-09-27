package by.gusto.auth.service;

import by.gusto.auth.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    /**
     * Лимит проверяется по двум ключам сразу: по связке ip+email и по самому
     * аккаунту. С одним ключом перебор пароля шёл с бота через меняющиеся адреса
     * и упирался только в лимит на пару ip+email (S44).
     */
    public boolean isAllowed(String endpointKey, String clientIp, String email) {
        return keys(endpointKey, clientIp, email).stream()
                .allMatch(this::underLimit);
    }

    public void recordAttempt(String endpointKey, String clientIp, String email) {
        long window = securityProperties.getRateLimit().getWindowMinutes();
        for (String key : keys(endpointKey, clientIp, email)) {
            Long current = redisTemplate.opsForValue().increment(key);
            if (current != null && current == 1) {
                redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(window));
            }
        }
    }

    private List<String> keys(String endpointKey, String clientIp, String email) {
        String safeIp = clientIp == null ? "unknown" : clientIp;
        String prefix = "rate:auth:" + endpointKey + ":";
        if (email == null || email.isBlank()) {
            return List.of(prefix + safeIp + ":unknown");
        }
        String safeEmail = email.toLowerCase();
        return List.of(prefix + safeIp + ":" + safeEmail, prefix + "acct:" + safeEmail);
    }

    private boolean underLimit(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return true;
        }
        try {
            return Integer.parseInt(value) < securityProperties.getRateLimit().getAttempts();
        } catch (NumberFormatException e) {
            // ключ испорчен — не блокируем вход из-за мусора в Redis
            return true;
        }
    }
}
