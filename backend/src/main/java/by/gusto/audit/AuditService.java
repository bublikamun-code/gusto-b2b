package by.gusto.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Запись в журнал аудита (таблица audit_log из V1). Наполняется действиями
 * заказов (S22), документов (S24) и импорта (S35); просмотр с фильтрами — S38.
 * Ошибка записи не должна ломать бизнес-операцию — логируем и продолжаем.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void append(UUID actorId, String action, String targetType, UUID targetId,
                       Map<String, Object> before, Map<String, Object> after) {
        try {
            jdbcTemplate.update(
                    "insert into audit_log (actor_id, action, target_type, target_id, before, after) "
                            + "values (?, ?, ?, ?, ?::jsonb, ?::jsonb)",
                    actorId, action, targetType, targetId, toJson(before), toJson(after));
        } catch (Exception e) {
            log.warn("Не удалось записать в audit_log: action={}, target={}", action, targetId, e);
        }
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
