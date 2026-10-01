package by.gusto.cabinet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Сводка для дашборда кабинета клиента.
 *
 * <p>Раньше дашборд был заглушкой из трёх ссылок: клиент, открывая кабинет,
 * не видел ни текущих заказов, ни состояния расчётов, и должен был наощупь
 * переходить по разделам (аудит 2026-09-30, доработка кабинета).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabinetSummaryResponse {

    private String companyName;

    /** Заказы в работе: NEW, IN_PROGRESS, SHIPPED. */
    private long activeOrdersCount;

    /** Сумма заказов в работе, BYN. */
    private BigDecimal activeOrdersTotal;

    /** Заказы, ожидающие подтверждения менеджером. */
    private long awaitingConfirmationCount;

    /** Выставленные счета: ISSUED и PARTIALLY_PAID. */
    private long unpaidInvoicesCount;

    /** Долг: сумма выставленных счетов минус зарегистрированные оплаты, BYN. */
    private BigDecimal outstandingDebt;

    /** Заказ, который клиент делал последним. */
    private String lastOrderNumber;
    private String lastOrderStatus;
    private Instant lastOrderAt;
}