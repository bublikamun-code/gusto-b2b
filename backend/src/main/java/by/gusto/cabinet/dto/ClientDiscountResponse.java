package by.gusto.cabinet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Скидка клиента для экрана «Прайс и скидки» в кабинете (матрица 2.1: клиент-юрлицо
 * видит свои цены и скидки).
 *
 * <p>{@code brandName}/{@code categoryName} заполнены настолько, насколько правило
 * ограничено: если правило без бренда — тут null, и это осмысленное «на весь каталог»,
 * а не «данных не нашлось».
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientDiscountResponse {

    private UUID id;

    /** Название бренда; null — правило действует на весь каталог. */
    private String brandName;

    /** Название категории; null — правило действует на весь каталог. */
    private String categoryName;

    private BigDecimal discountPercent;
    private LocalDate validFrom;
    private LocalDate validTo;

    /** true, если срок действия покрывает сегодняшний день. */
    private boolean active;
}