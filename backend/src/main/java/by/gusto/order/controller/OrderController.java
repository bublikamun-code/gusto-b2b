package by.gusto.order.controller;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.order.dto.OrderDtos.CreateRequest;
import by.gusto.order.dto.OrderDtos.Response;
import by.gusto.order.service.IdempotencyService;
import by.gusto.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final AuthContext authContext;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<Response>> create(
            @Valid @RequestBody CreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        User user = authContext.getCurrentUser();
        String requestHash = idempotencyService.sha256(serialize(request));

        // Повтор с тем же ключом и телом → сохранённый ответ, новый заказ не создаётся (1.6)
        var saved = idempotencyService.findCompleted(idempotencyKey, "/orders", requestHash);
        if (saved.isPresent()) {
            Response response = objectMapper.convertValue(saved.get(), Response.class);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
        }

        Response response = orderService.create(request, user);
        idempotencyService.store(idempotencyKey, "/orders", user.getId(), requestHash, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Response>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = authContext.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(orderService.listVisible(user, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Response>> get(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getById(id, authContext.getCurrentUser())));
    }

    private String serialize(CreateRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new GustoException(ErrorCode.INTERNAL, "Не удалось сериализовать запрос");
        }
    }
}
