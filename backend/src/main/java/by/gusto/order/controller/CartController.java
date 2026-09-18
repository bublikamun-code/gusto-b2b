package by.gusto.order.controller;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.order.dto.OrderDtos.CartItemPutRequest;
import by.gusto.order.dto.OrderDtos.CartResponse;
import by.gusto.order.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Персональная корзина (4.1): GET /cart, PUT /cart/items/{productId} (0 = удалить),
 * DELETE /cart. Общая корзины на компанию в MVP нет (1.6).
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final AuthContext authContext;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart() {
        User user = authContext.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(
                cartService.getCart(user.getId(), user.getCompanyId())));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> putItem(
            @PathVariable java.util.UUID productId,
            @Valid @RequestBody CartItemPutRequest request) {
        User user = authContext.getCurrentUser();
        cartService.putItem(user.getId(), productId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success(
                cartService.getCart(user.getId(), user.getCompanyId())));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<CartResponse>> clearCart() {
        User user = authContext.getCurrentUser();
        cartService.clear(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
                cartService.getCart(user.getId(), user.getCompanyId())));
    }
}
