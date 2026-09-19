package by.gusto.audit;

import by.gusto.audit.dto.AuditEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Чтение журнала аудита с фильтрами (S38): действие, тип/ид объекта, актер,
 * период. Записи пишут модули заказов (S22), документов (S24), импорта (S35)
 * и настроек (S38). Чтение — только ADMIN (матрица 2.1 «Аудит»).
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SearchResult search(String action, String targetType, UUID targetId,
                               UUID actorId, Instant dateFrom, Instant dateTo,
                               int page, int size) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            where.append(" and a.action ilike ?");
            args.add(action.trim() + "%");
        }
        if (targetType != null && !targetType.isBlank()) {
            where.append(" and a.target_type = ?");
            args.add(targetType.trim());
        }
        if (targetId != null) {
            where.append(" and a.target_id = ?");
            args.add(targetId);
        }
        if (actorId != null) {
            where.append(" and a.actor_id = ?");
            args.add(actorId);
        }
        if (dateFrom != null) {
            where.append(" and a.created_at >= ?");
            args.add(Timestamp.from(dateFrom));
        }
        if (dateTo != null) {
            where.append(" and a.created_at < ?");
            args.add(Timestamp.from(dateTo));
        }

        String base = " from audit_log a left join users u on u.id = a.actor_id" + where;
        Long total = jdbcTemplate.queryForObject("select count(*)" + base, Long.class, args.toArray());
        if (total == null || total == 0) {
            return new SearchResult(List.of(), 0);
        }
        String sql = "select a.id, a.actor_id, u.email as actor_email, u.full_name as actor_name, "
                + "a.action, a.target_type, a.target_id, a.before, a.after, a.created_at" + base
                + " order by a.created_at desc limit ? offset ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<AuditEntry> items = jdbcTemplate.query(sql, (rs, i) -> new AuditEntry(
                        rs.getObject("id", UUID.class),
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("actor_email"),
                        rs.getString("actor_name"),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getObject("target_id", UUID.class),
                        json(rs.getString("before")),
                        json(rs.getString("after")),
                        rs.getTimestamp("created_at").toInstant()),
                pageArgs.toArray());
        return new SearchResult(items, total);
    }

    public record SearchResult(List<AuditEntry> items, long total) {
    }

    private com.fasterxml.jackson.databind.JsonNode json(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            return null;
        }
    }
}
