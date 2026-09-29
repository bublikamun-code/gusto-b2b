package by.gusto.ai.controller;

import by.gusto.ai.config.AiProperties;
import by.gusto.ai.dto.AiAdvisorDtos.ChatRequest;
import by.gusto.ai.dto.AiAdvisorDtos.ChatResponse;
import by.gusto.ai.dto.AiAdvisorDtos.RecipeSummary;
import by.gusto.ai.service.AiAdvisorService;
import by.gusto.common.api.ApiResponse;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Публичный ИИ-советник лендинга (S45): рецепты и диалог без авторизации.
 * Блок рецептов отдаётся всегда, чат — с rate limit по IP и только при
 * app.ai.enabled, иначе витрина падает на ровном месте из-за флага в .env.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiAdvisorController {

    private final AiAdvisorService advisorService;
    private final AiProperties properties;

    @GetMapping("/recipes")
    public ResponseEntity<ApiResponse<List<RecipeSummary>>> recipes(
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success(advisorService.recommendRecipes(limit)));
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest http) {
        if (!properties.isEnabled()) {
            throw new GustoException(ErrorCode.NOT_FOUND, "ИИ-советник выключен");
        }
        return ResponseEntity.ok(ApiResponse.success(advisorService.chat(request, clientIp(http))));
    }

    /** Последний элемент XFF дописывает наш прокси (как в SiteRequestController, S31). */
    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String last = hops[hops.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        return http.getRemoteAddr();
    }
}
