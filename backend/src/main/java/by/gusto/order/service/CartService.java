package by.gusto.order.service;

import by.gusto.catalog.entity.Product;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.catalog.service.RetailPriceService;
import by.gusto.catalog.service.pricing.PricingService;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.inventory.service.StockService;
import by.gusto.order.dto.OrderDtos.CartItemResponse;
import by.gusto.order.dto.OrderDtos.CartResponse;
import by.gusto.order.entity.Cart;
import by.gusto.order.entity.CartItem;
import by.gusto.order.repository.CartItemRepository;
import by.gusto.order.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Персональная корзина в БД (1.6): одна на пользователя, позиции с ценой клиента
 * на момент чтения. Цена фиксируется снапшотом только в момент создания заказа (S20).
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final RetailPriceService retailPriceService;
    private final PricingService pricingService;

    @Transactional
    public Cart getOrCreateCart(UUID userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().userId(userId).build()));
    }

    /** quantity = 0 удаляет позицию (4.1). */
    @Transactional
    public void putItem(UUID userId, UUID productId, BigDecimal quantity) {
        Cart cart = getOrCreateCart(userId);
        if (quantity.signum() <= 0) {
            cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                    .ifPresent(cartItemRepository::delete);
            return;
        }
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseGet(() -> CartItem.builder()
                        .cartId(cart.getId())
                        .productId(productId)
                        .quantity(BigDecimal.ZERO)
                        .build());
        item.setQuantity(quantity);
        cartItemRepository.save(item);
    }

    @Transactional
    public void clear(UUID userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> cartItemRepository.deleteAllByCartId(cart.getId()));
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userId, UUID companyId) {
        CartResponse response = new CartResponse();
        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;

        Cart cart = getOrCreateCart(userId);
        List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart.getId());
        if (!cartItems.isEmpty()) {
            Map<UUID, Product> products = productRepository
                    .findAllById(cartItems.stream().map(CartItem::getProductId).toList())
                    .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
            for (CartItem cartItem : cartItems) {
                Product product = products.get(cartItem.getProductId());
                if (product == null || !product.isActive() || product.getDeletedAt() != null) {
                    continue; // товар удалили/скрыли — не показываем в корзине
                }
                BigDecimal price = resolvePrice(userId, companyId, product);
                CartItemResponse item = new CartItemResponse();
                item.setProductId(product.getId());
                item.setSku(product.getSku());
                item.setProductName(product.getName());
                item.setUnit(product.getUnit());
                item.setQuantity(cartItem.getQuantity());
                item.setUnitPrice(price);
                BigDecimal lineTotal = price.multiply(cartItem.getQuantity()).setScale(2, RoundingMode.HALF_UP);
                item.setTotal(lineTotal);
                totalAmount = totalAmount.add(lineTotal);
                totalVat = totalVat.add(vatIncluded(product.getVatRate(), lineTotal));
                items.add(item);
            }
        }
        response.setItems(items);
        response.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        response.setTotalVat(totalVat.setScale(2, RoundingMode.HALF_UP));
        return response;
    }

    /** Цена клиента (2.5): персональная → скидка → базовая розница. */
    public BigDecimal resolvePrice(UUID userId, UUID companyId, Product product) {
        Optional<BigDecimal> price = companyId != null
                ? pricingService.getCustomerPrice(companyId, product)
                : Optional.empty();
        return price
                .or(() -> retailPriceService.getRetailPrice(product.getId(), LocalDate.now()))
                .orElseThrow(() -> new GustoException(ErrorCode.VALIDATION_FAILED,
                        "Для товара " + product.getSku() + " не задана цена"));
    }

    /** НДС-включённая цена (2.3): vat = total × rate / (100 + rate). */
    public static BigDecimal vatIncluded(BigDecimal vatRate, BigDecimal total) {
        return total.multiply(vatRate)
                .divide(BigDecimal.valueOf(100).add(vatRate), 2, RoundingMode.HALF_UP);
    }
}
