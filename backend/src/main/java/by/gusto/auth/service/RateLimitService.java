package by.gusto.auth.service;

import by.gusto.auth.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    public boolean isAllowed(String endpointKey, String clientIp, String email) {
        String key = buildKey(endpointKey, clientIp, email);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return true;
        }
        int attempts = Integer.parseInt(value);
        return attempts < securityProperties.getRateLimit().getAttempts();
    }

    public void recordAttempt(String endpointKey, String clientIp, String email) {
        String key = buildKey(endpointKey, clientIp, email);
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1) {
            redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(securityProperties.getRateLimit().getWindowMinutes()));
        }
    }

    private String buildKey(String endpointKey, String clientIp, String email) {
        String safeEmail = email == null ? "unknown" : email.toLowerCase();
        String safeIp = clientIp == null ? "unknown" : clientIp;
        return "rate:auth:" + endpointKey + ":" + safeIp + ":" + safeEmail;
    }
}
