package by.gusto.order.service;

import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.order.entity.IdempotencyKey;
import by.gusto.order.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Идемпотентность POST /orders (1.6): повтор с тем же ключом и тем же телом
 * возвращает сохранённый результат; тот же ключ с другим телом — конфликт.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findCompleted(String key, String endpoint, String requestHash) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return repository.findByKey(key).flatMap(saved -> {
            if (!saved.getEndpoint().equals(endpoint) || !saved.getRequestHash().equals(requestHash)) {
                throw new GustoException(ErrorCode.IDEMPOTENCY_CONFLICT,
                        "Идемпотентный ключ уже использован с другим запросом");
            }
            return Optional.ofNullable(saved.getResponse());
        });
    }

    /** Отдельная транзакция: результат заказа сохраняется, даже если вызывающая tx уже закрыта. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(String key, String endpoint, UUID userId, String requestHash, Object response) {
        if (key == null || key.isBlank()) {
            return;
        }
        Map<String, Object> asMap = objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {
        });
        try {
            repository.save(IdempotencyKey.builder()
                    .key(key)
                    .endpoint(endpoint)
                    .userId(userId)
                    .requestHash(requestHash)
                    .response(asMap)
                    .expiresAt(Instant.now().plus(TTL))
                    .build());
        } catch (DataIntegrityViolationException e) {
            // параллельный дубль с тем же ключом; ответ уже сохранён победителем
        }
    }

    public String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
