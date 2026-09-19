package by.gusto.audit;

import by.gusto.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Журнал аудита с фильтрами (S38): только ADMIN (матрица 2.1 «Аудит»).
 * Фильтры: префикс действия, тип/идентификатор объекта, актер, период.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditQueryService queryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditEntryDto>>> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Instant from = dateFrom == null ? null : dateFrom.atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant to = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant();
        AuditQueryService.SearchResult result = queryService.search(
                action, targetType, targetId, actorId, from, to, safePage, safeSize);
        return ResponseEntity.ok(ApiResponse.success(
                result.items().stream().map(AuditEntryDto::from).toList(),
                Map.of("page", safePage, "size", safeSize, "total", result.total())));
    }

    /** Плоский DTO: before/after как произвольные JSON-объекты для таблицы. */
    public record AuditEntryDto(java.util.UUID id, java.util.UUID actorId, String actorEmail,
                                String actorName, String action, String targetType,
                                java.util.UUID targetId, Object before, Object after,
                                Instant createdAt) {

        static AuditEntryDto from(by.gusto.audit.dto.AuditEntry e) {
            return new AuditEntryDto(e.id(), e.actorId(), e.actorEmail(), e.actorName(),
                    e.action(), e.targetType(), e.targetId(), e.before(), e.after(), e.createdAt());
        }
    }
}
