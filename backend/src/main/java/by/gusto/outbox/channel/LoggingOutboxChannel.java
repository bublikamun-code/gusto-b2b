package by.gusto.outbox.channel;

import by.gusto.outbox.entity.OutboxMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Канал по умолчанию (S31): журналирует доставку. Имеет наименьший приоритет —
 * специализированные каналы (EMAIL_* S33, TG_* S32) выбираются первыми.
 */
@Component
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class LoggingOutboxChannel implements OutboxChannel {

    @Override
    public boolean supports(String type) {
        return true;
    }

    @Override
    public boolean send(OutboxMessage message) {
        log.info("OUTBOX {} [{}] aggregate={}/{}: {}",
                message.getId(), message.getType(), message.getAggregateType(),
                message.getAggregateId(), message.getPayload());
        return true;
    }
}
