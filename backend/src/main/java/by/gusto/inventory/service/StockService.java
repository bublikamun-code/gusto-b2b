package by.gusto.inventory.service;

import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.common.settings.SettingsService;
import by.gusto.inventory.entity.StockBalance;
import by.gusto.inventory.entity.StockMovement;
import by.gusto.inventory.repository.StockBalanceRepository;
import by.gusto.inventory.repository.StockLocationRepository;
import by.gusto.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Складские операции (S18). Инварианты:
 *  - {@code stock_balances} — единственное место конкурентного доступа (FOR UPDATE, 1.6);
 *  - любое изменение баланса сопровождается строкой в журнале {@code stock_movements}
 *    в той же транзакции;
 *  - INCOMING/OUTGOING/ADJUSTMENT меняют quantity, RESERVE/RELEASE — только reserved (3.1).
 */
@Service
@RequiredArgsConstructor
public class StockService {

    public static final String DEFAULT_LOCATION_SETTING = "stock.default_location";

    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;
    private final StockLocationRepository locationRepository;
    private final SettingsService settingsService;

    /** Склад резерва заказа (1.6 «Склад и заказ»). */
    @Transactional(readOnly = true)
    public UUID defaultLocationId() {
        Optional<UUID> fromSettings = settingsService.getUuid(DEFAULT_LOCATION_SETTING);
        return fromSettings.orElseGet(() -> locationRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .findFirst()
                .orElseThrow(() -> new GustoException(ErrorCode.INTERNAL,
                        "Не задан склад по умолчанию: settings('stock.default_location') и stock_locations пусты"))
                .getId());
    }

    /**
     * Резерв в транзакции заказа: лочит строку баланса, параллельный заказ ждёт
     * и видит уже уменьшенный доступный остаток. Нехватка → STOCK_INSUFFICIENT.
     */
    @Transactional
    public void reserve(UUID productId, UUID locationId, BigDecimal quantity,
                        String referenceType, UUID referenceId, UUID createdBy) {
        if (quantity.signum() <= 0) {
            return;
        }
        StockBalance balance = lockBalance(productId, locationId);
        if (balance.getReserved().add(quantity).compareTo(balance.getQuantity()) > 0) {
            throw new GustoException(ErrorCode.STOCK_INSUFFICIENT,
                    "Недостаточно остатка: доступно " + balance.available() + ", требуется " + quantity);
        }
        balance.setReserved(balance.getReserved().add(quantity));
        balance.setUpdatedAt(Instant.now());
        balanceRepository.save(balance);
        movementRepository.save(StockMovement.builder()
                .productId(productId)
                .locationId(locationId)
                .type(StockMovement.Type.RESERVE)
                .quantity(quantity)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .createdBy(createdBy)
                .build());
    }

    /** Освобождение резерва (отмена заказа, отгрузка). */
    @Transactional
    public void release(UUID productId, UUID locationId, BigDecimal quantity,
                        String referenceType, UUID referenceId, UUID createdBy) {
        if (quantity.signum() <= 0) {
            return;
        }
        StockBalance balance = lockBalance(productId, locationId);
        if (balance.getReserved().compareTo(quantity) < 0) {
            throw new GustoException(ErrorCode.STOCK_DOCUMENT_INVALID,
                    "Попытка освободить больше зарезервированного: reserved=" + balance.getReserved());
        }
        balance.setReserved(balance.getReserved().subtract(quantity));
        balance.setUpdatedAt(Instant.now());
        balanceRepository.save(balance);
        movementRepository.save(StockMovement.builder()
                .productId(productId)
                .locationId(locationId)
                .type(StockMovement.Type.RELEASE)
                .quantity(quantity)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .createdBy(createdBy)
                .build());
    }

    /**
     * Движение товара: INCOMING/OUTGOING принимают положительное количество и применяют
     * знак по типу (3.1: INCOMING +, OUTGOING −), ADJUSTMENT — знакое значение;
     * RESERVE/RELEASE меняют только reserved. Вызывается при CONFIRMED складского
     * документа (S18.1). Журнал хранит знаковое значение.
     */
    @Transactional
    public void applyMovement(UUID productId, UUID locationId, StockMovement.Type type, BigDecimal quantity,
                              String referenceType, UUID referenceId, String note, UUID createdBy) {
        if (quantity.signum() == 0) {
            return;
        }
        StockBalance balance = lockBalance(productId, locationId);
        BigDecimal journalQuantity = quantity;
        switch (type) {
            case INCOMING -> {
                journalQuantity = quantity.abs();
                balance.setQuantity(balance.getQuantity().add(journalQuantity));
            }
            case OUTGOING -> {
                journalQuantity = quantity.abs().negate();
                BigDecimal newQuantity = balance.getQuantity().add(journalQuantity);
                if (newQuantity.subtract(balance.getReserved()).signum() < 0) {
                    throw new GustoException(ErrorCode.STOCK_INSUFFICIENT,
                            "Движение уводит доступный остаток в минус: доступно " + balance.available());
                }
                balance.setQuantity(newQuantity);
            }
            case ADJUSTMENT -> {
                BigDecimal newQuantity = balance.getQuantity().add(quantity);
                if (newQuantity.subtract(balance.getReserved()).signum() < 0) {
                    throw new GustoException(ErrorCode.STOCK_INSUFFICIENT,
                            "Корректировка уводит доступный остаток в минус: доступно " + balance.available());
                }
                balance.setQuantity(newQuantity);
            }
            case RESERVE -> {
                BigDecimal newReserved = balance.getReserved().add(quantity);
                if (newReserved.compareTo(balance.getQuantity()) > 0) {
                    throw new GustoException(ErrorCode.STOCK_INSUFFICIENT, "Недостаточно остатка для резерва");
                }
                balance.setReserved(newReserved);
            }
            case RELEASE -> {
                BigDecimal newReserved = balance.getReserved().subtract(quantity);
                if (newReserved.signum() < 0) {
                    throw new GustoException(ErrorCode.STOCK_DOCUMENT_INVALID,
                            "Попытка освободить больше зарезервированного");
                }
                balance.setReserved(newReserved);
            }
        }
        balance.setUpdatedAt(Instant.now());
        balanceRepository.save(balance);
        movementRepository.save(StockMovement.builder()
                .productId(productId)
                .locationId(locationId)
                .type(type)
                .quantity(journalQuantity)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .note(note)
                .createdBy(createdBy)
                .build());
    }

    /** Доступный остаток набора товаров на складе (для статуса наличия). */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> availableByProduct(UUID locationId, Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return balanceRepository.findAvailableByLocationAndProductIds(locationId, productIds).stream()
                .collect(Collectors.toMap(
                        StockBalanceRepository.AvailableProjection::getProductId,
                        StockBalanceRepository.AvailableProjection::getAvailable));
    }

    private StockBalance lockBalance(UUID productId, UUID locationId) {
        return balanceRepository.findForUpdate(productId, locationId)
                .orElseGet(() -> StockBalance.builder()
                        .productId(productId)
                        .locationId(locationId)
                        .quantity(BigDecimal.ZERO)
                        .reserved(BigDecimal.ZERO)
                        .updatedAt(Instant.now())
                        .build());
    }
}
