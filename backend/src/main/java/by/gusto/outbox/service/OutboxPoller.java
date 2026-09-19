package by.gusto.outbox.service;

import by.gusto.outbox.channel.OutboxChannel;
import by.gusto.outbox.entity.OutboxMessage;
import by.gusto.outbox.repository.OutboxMessageRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Поллер outbox (S31, 1.6): забирает сообщения PENDING с наступившим сроком
 * (FOR UPDATE SKIP LOCKED — безопасно при нескольких инстансах), доставляет
 * через канал (Resilience4j CircuitBreaker), при сбое — ретрай по backoff
 * 1/5/15/60 мин, после 5 попыток — FAILED. Раз в 10 секунд; в тестах — pollOnce().
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    public static final int MAX_ATTEMPTS = 5;
    private static final int BATCH = 20;
    private static final Duration[] BACKOFF = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(60)
    };

    private final OutboxMessageRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final List<OutboxChannel> channels;
    private final CircuitBreaker circuitBreaker = CircuitBreaker.of("outbox",
            CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .minimumNumberOfCalls(5)
                    .slidingWindowSize(10)
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .build());

    /** Специализированный канал важнее канала-по-умолчанию (лог). */
    private OutboxChannel channelFor(String type) {
        OutboxChannel fallback = null;
        for (OutboxChannel channel : channels) {
            if (!(channel instanceof by.gusto.outbox.channel.LoggingOutboxChannel)
                    && channel.supports(type)) {
                return channel;
            }
            if (channel instanceof by.gusto.outbox.channel.LoggingOutboxChannel) {
                fallback = channel;
            }
        }
        if (fallback == null) {
            throw new IllegalStateException("Нет канала для типа " + type);
        }
        return fallback;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:10000}")
    public void scheduled() {
        pollOnce();
    }

    /** Один проход поллера; используется и планировщиком, и тестами. */
    @Transactional
    public void pollOnce() {
        List<UUID> ids = jdbcTemplate.queryForList(
                "select id from outbox_messages "
                        + "where status = 'PENDING' and next_attempt_at <= now() "
                        + "order by next_attempt_at limit ? for update skip locked",
                UUID.class, BATCH);
        if (ids.isEmpty()) {
            return;
        }
        for (OutboxMessage message : repository.findAllById(ids)) {
            dispatch(message);
        }
    }

    private void dispatch(OutboxMessage message) {
        try {
            boolean sent = circuitBreaker.executeSupplier(() -> channelFor(message.getType()).send(message));
            if (sent) {
                message.setStatus(OutboxMessage.Status.SENT);
                message.setSentAt(Instant.now());
                repository.save(message);
                return;
            }
            scheduleRetry(message);
        } catch (Exception e) {
            log.warn("OUTBOX {} сбой доставки (попытка {}): {}",
                    message.getId(), message.getAttempts() + 1, e.getMessage());
            scheduleRetry(message);
        }
    }

    private void scheduleRetry(OutboxMessage message) {
        int attempt = message.getAttempts() + 1;
        message.setAttempts(attempt);
        if (attempt >= MAX_ATTEMPTS) {
            message.setStatus(OutboxMessage.Status.FAILED);
        } else {
            message.setNextAttemptAt(Instant.now().plus(BACKOFF[Math.min(attempt, BACKOFF.length) - 1]));
        }
        repository.save(message);
    }
}
