package by.gusto.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Запись в журнал аудита (таблица audit_log из V1). Просмотр с фильтрами — S38.
 *
 * <p>Сама вставка живёт в {@link AuditWriter} и идёт в отдельной транзакции: если
 * транзакция бизнес-операции уже прервана, запись в журнал всё равно должна
 * сохраниться, а погасить ошибку внутри прерванной транзакции невозможно
 * (аудит 2026-09-30, находка P2-15 с 2026-09-23).
 *
 * <p>Ошибка записи не должна ломать бизнес-операцию — логируем и продолжаем.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditWriter auditWriter;

    public void append(UUID actorId, String action, String targetType, UUID targetId,
                       Map<String, Object> before, Map<String, Object> after) {
        try {
            auditWriter.write(actorId, action, targetType, targetId, before, after);
        } catch (Exception e) {
            log.warn("Не удалось записать в audit_log: action={}, target={}", action, targetId, e);
        }
    }
}