package by.gusto.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    @Data
    public static class PaymentRequest {

        @NotNull
        @Positive
        private BigDecimal amount;

        /** Дата оплаты; по умолчанию — сегодня. */
        private LocalDate paidAt;

        /** Способ оплаты (наличные, безнал, ЕРИП...). */
        private String method;

        private String note;
    }

    public record PaymentResponse(
            UUID id,
            UUID invoiceId,
            BigDecimal amount,
            LocalDate paidAt,
            String method,
            String note,
            Instant createdAt) {
    }

    /** Строка отчёта «задолженность по клиентам» (S28). */
    public record DebtRow(
            UUID companyId,
            String companyName,
            BigDecimal invoiced,
            BigDecimal paid,
            BigDecimal debt,
            Instant oldestActiveAt) {
    }
}
