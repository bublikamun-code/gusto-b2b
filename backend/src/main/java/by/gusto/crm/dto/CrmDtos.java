package by.gusto.crm.dto;

import by.gusto.crm.entity.LeadEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CrmDtos {

    private CrmDtos() {
    }

    // ----- лиды ------------------------------------------------------------------

    @Data
    public static class CreateLeadRequest {

        @NotBlank
        private String name;

        private String phone;
        private String email;
        private String companyName;
        private String message;
        private String source;

        /** Опционально: сразу назначить менеджера. */
        private UUID managerId;
    }

    @Data
    public static class AssignLeadRequest {

        @NotNull
        private UUID managerId;
    }

    @Data
    public static class LeadStatusRequest {

        @NotNull
        private LeadEntity.Status status;
    }

    // ----- задачи ----------------------------------------------------------------

    @Data
    public static class CreateTaskRequest {

        @NotBlank
        private String title;

        private String description;

        /** По умолчанию — автор запроса. */
        private UUID assigneeId;

        private UUID companyId;

        private Instant dueDate;
    }

    // ----- заметки ---------------------------------------------------------------

    @Data
    public static class CreateNoteRequest {

        @NotNull
        private UUID companyId;

        @NotBlank
        private String body;
    }

    // ----- ответы ----------------------------------------------------------------

    public record LeadResponse(
            UUID id, String source, String name, String phone, String email,
            String companyName, String message, String status,
            UUID assignedManagerId, Instant createdAt) {
    }

    public record TaskResponse(
            UUID id, UUID assigneeId, UUID companyId, String title, String description,
            Instant dueDate, String status, boolean overdue, Instant createdAt) {
    }

    public record NoteResponse(
            UUID id, UUID companyId, UUID authorId, String authorName, String body, Instant createdAt) {
    }

    /** Дашборд руководителя (S29). */
    public record DashboardResponse(
            BigDecimal revenue,
            long completedOrders,
            List<Map<String, Object>> topProducts,
            List<Map<String, Object>> topCustomers,
            BigDecimal debt,
            long leadsTotal,
            long leadsWon,
            double leadConversionPercent) {
    }
}
