package by.gusto.request.controller;

import by.gusto.common.api.ApiResponse;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.order.service.IdempotencyService;
import by.gusto.request.dto.SiteRequestDtos.CreateRequest;
import by.gusto.request.dto.SiteRequestDtos.SiteRequestResponse;
import by.gusto.request.entity.SiteRequestEntity;
import by.gusto.request.service.SiteRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.UUID;

/**
 * Публичные заявки с сайта (S31): без авторизации, rate limit по IP,
 * идемпотентность по заголовку Idempotency-Key (1.6). Заявка сразу
 * конвертируется в лид в пул «не назначено» (2.7).
 */
@RestController
@RequestMapping("/api/v1/site/requests")
@RequiredArgsConstructor
public class SiteRequestController {

    private final SiteRequestService siteRequestService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<SiteRequestResponse>> create(
            @Valid @RequestBody CreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest http) {
        String clientIp = clientIp(http);
        if (!siteRequestService.isAllowed(clientIp)) {
            throw new GustoException(ErrorCode.RATE_LIMITED);
        }
        siteRequestService.recordAttempt(clientIp);

        String requestHash = idempotencyService.sha256(serialize(request));
        var saved = idempotencyService.findCompleted(idempotencyKey, "/site/requests", requestHash);
        if (saved.isPresent()) {
            SiteRequestResponse response = objectMapper.convertValue(saved.get(), SiteRequestResponse.class);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
        }

        var created = siteRequestService.create(request, idempotencyKey);
        idempotencyService.store(idempotencyKey, "/site/requests", null, requestHash, created.request());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created.request()));
    }

    private String serialize(CreateRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new GustoException(ErrorCode.INTERNAL, "Не удалось сериализовать запрос");
        }
    }

    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
    }
}
