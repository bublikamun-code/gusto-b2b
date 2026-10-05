package by.gusto.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Физическая запись в {@code audit_log} в ОТДЕЛЬНОЙ транзакции (аудит 2026-09-30).
 *
 * <p>Раньше метод жил в {@link AuditService} и ловил исключение прямо в той же
 * транзакции, что и бизнес-операция. Это бессмысленно по двум причинам:
 * <ul>
 *   <li>любая ошибка вставки уже помечает транзакцию прерванной (PostgreSQL 25P02) —
 *       «мягко» её погасить нельзя, бизнес-операция всё равно откатится;</li>
 *   <li>{@code @Transactional} на методе того же бина из-за self-invocation не
 *       применяется прокси, поэтому объявить REQUIRES_NEW в старом коде было негде.</li>
 * </ul>
 *
 * <p>Вынесенный компонент даёт честную семантику: запись в журнал коммитится независимо
 * от исхода бизнес-операции — именно это и требуется от аудита.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(UUID actorId, String action, String targetType, UUID targetId,
                      Map<String, Object> before, Map<String, Object> after) {
        jdbcTemplate.update(
                "insert into audit_log (actor_id, action, target_type, target_id, before, after) "
                        + "values (?, ?, ?, ?, ?::jsonb, ?::jsonb)",
                actorId, action, targetType, targetId, toJson(before), toJson(after));
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("AUDIT: не удалось сериализовать снапшот для {}", value.keySet());
            return null;
        }
    }
}