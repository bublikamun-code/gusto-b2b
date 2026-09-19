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

    /** Все специализированные каналы типа; если ни один — канал-лог (низший приоритет). */
    private List<OutboxChannel> channelsFor(String type) {
        List<OutboxChannel> specific = channels.stream()
                .filter(c -> !(c instanceof by.gusto.outbox.channel.LoggingOutboxChannel))
                .filter(c -> c.supports(type))
                .toList();
        if (!specific.isEmpty()) {
            return specific;
        }
        return channels.stream()
                .filter(c -> c instanceof by.gusto.outbox.channel.LoggingOutboxChannel)
                .filter(c -> c.supports(type))
                .toList();
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

    /**
     * Событие уходит во ВСЕ подходящие каналы (Telegram + Email обрабатывают
     * одни и те же бизнес-события по разным подпискам). Семантика at-least-once:
     * при сбое любого канала сообщение ретраится целиком.
     */
    private void dispatch(OutboxMessage message) {
        List<OutboxChannel> targets = channelsFor(message.getType());
        try {
            boolean allSent = true;
            for (OutboxChannel channel : targets) {
                boolean sent = circuitBreaker.executeSupplier(() -> channel.send(message));
                allSent = allSent && sent;
            }
            if (allSent) {
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
