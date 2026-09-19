package by.gusto.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Элемент журнала аудита (S38): актер денормализован для списка. */
public record AuditEntry(UUID id, UUID actorId, String actorEmail, String actorName,
                         String action, String targetType, UUID targetId,
                         JsonNode before, JsonNode after, Instant createdAt) {
}
