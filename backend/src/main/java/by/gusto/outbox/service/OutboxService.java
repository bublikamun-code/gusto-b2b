package by.gusto.outbox.service;

import by.gusto.outbox.entity.OutboxMessage;
import by.gusto.outbox.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Запись событий в outbox в транзакции бизнес-операции (1.6).
 * Отправка — поллер S31.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    public static final String TYPE_EMAIL_CONFIRMATION = "EMAIL_CONFIRMATION";

    private final OutboxMessageRepository repository;

    @Transactional
    public OutboxMessage append(String aggregateType, UUID aggregateId, String type, Map<String, Object> payload) {
        OutboxMessage message = OutboxMessage.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .type(type)
                .payload(payload)
                .build();
        return repository.save(message);
    }
}
