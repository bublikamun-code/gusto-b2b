package by.gusto.outbox.repository;

import by.gusto.outbox.entity.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId);
}
