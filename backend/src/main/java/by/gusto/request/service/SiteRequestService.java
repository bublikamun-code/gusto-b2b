package by.gusto.request.service;

import by.gusto.common.api.Pages;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.crm.entity.LeadEntity;
import by.gusto.crm.repository.LeadRepository;
import by.gusto.request.dto.SiteRequestDtos.CreateRequest;
import by.gusto.request.dto.SiteRequestDtos.SiteRequestResponse;
import by.gusto.request.entity.SiteRequestEntity;
import by.gusto.request.entity.SiteRequestEntity.Status;
import by.gusto.request.repository.SiteRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;
import java.util.Map;

/**
 * Заявки с сайта (S31): публичное создание с rate limit и идемпотентностью (1.6).
 * Заявка сразу конвертируется в лид в пул «не назначено» (2.7), событие — в outbox
 * (уведомление всем менеджерам, 2.6; доставка — поллер S31 + каналы S32/S33).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SiteRequestService {

    private static final String RATE_KEY = "site-request";
    private static final int RATE_LIMIT = 10;

    private final SiteRequestRepository siteRequestRepository;
    private final LeadRepository leadRepository;
    private final by.gusto.outbox.service.OutboxService outboxService;
    private final StringRedisTemplate redisTemplate;

    /** Публично: заявка + лид в пул + событие outbox. Возвращает пару ответов. */
    @Transactional
    public Created create(CreateRequest request, String idempotencyKey) {
        SiteRequestEntity entity = siteRequestRepository.save(SiteRequestEntity.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .message(request.getMessage())
                .type(request.getType())
                .build());

        // лид из заявки — сразу в пул «не назначено» (без manager_id)
        LeadEntity lead = leadRepository.save(LeadEntity.builder()
                .source(request.getType().name())
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .companyName(request.getName())
                .message(request.getMessage())
                .build());

        entity.setLeadId(lead.getId());
        entity = siteRequestRepository.save(entity);

        outboxService.append("site_request", entity.getId(), "SITE_REQUEST_CREATED", Map.of(
                "requestId", entity.getId().toString(),
                "leadId", lead.getId().toString(),
                "name", String.valueOf(entity.getName()),
                "type", entity.getType().name()));

        return new Created(toResponse(entity), lead.getId());
    }

    /**
     * Rate limit публичной формы: не более 10 заявок в час с одного IP (1.6).
     *
     * <p>Fail-open: при недоступном Redis раньше летел RedisConnectionFailureException,
     * generic-обработчик отдавал 500 и публичная форма «Стать клиентом» не работала вовсе —
     * хотя принять заявку можно было и нужно (аудит 2026-09-30, P1-19). ИИ-лимитер в S45
     * сделали fail-open, а этот остался fail-closed. Счётчик также разбирается безопасно:
     * битое значение больше не даёт 500.
     */
    public boolean isAllowed(String clientIp) {
        try {
            String value = redisTemplate.opsForValue().get(rateKey(clientIp));
            return value == null || parseCount(value) < RATE_LIMIT;
        } catch (Exception e) {
            log.warn("RATE LIMIT: Redis недоступен, заявка пропущена ({}): {}", rateKey(clientIp), e.toString());
            return true;
        }
    }

    public void recordAttempt(String clientIp) {
        String key = rateKey(clientIp);
        try {
            Long current = redisTemplate.opsForValue().increment(key);
            if (current != null && current == 1) {
                redisTemplate.opsForValue().set(key, "1", Duration.ofHours(1));
            }
        } catch (Exception e) {
            log.warn("RATE LIMIT: не удалось учесть попытку ({}): {}", key, e.toString());
        }
    }

    private String rateKey(String clientIp) {
        return "rate:site-request:" + (clientIp == null ? "unknown" : clientIp);
    }

    private int parseCount(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ----- менеджерская часть ----------------------------------------------------

    @Transactional(readOnly = true)
    public Page<SiteRequestResponse> list(User actor, Status status, int page, int size) {
        PageRequest pageable = Pages.of(page, size);
        Page<SiteRequestEntity> requests = status == null
                ? siteRequestRepository.findAllByOrderByCreatedAtDesc(pageable)
                : siteRequestRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
        return requests.map(this::toResponse);
    }

    @Transactional
    public SiteRequestResponse changeStatus(UUID id, Status target, User actor) {
        SiteRequestEntity request = siteRequestRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Заявка не найдена"));
        Status current = request.getStatus();
        boolean allowed = current == Status.NEW && target == Status.IN_PROGRESS
                || current == Status.IN_PROGRESS && target == Status.CLOSED;
        if (!allowed) {
            throw new GustoException(ErrorCode.CONFLICT,
                    "Переход " + current + " → " + target + " недопустим");
        }
        request.setStatus(target);
        request = siteRequestRepository.save(request);
        return toResponse(request);
    }

    private SiteRequestResponse toResponse(SiteRequestEntity entity) {
        return new SiteRequestResponse(entity.getId(), entity.getName(), entity.getPhone(),
                entity.getEmail(), entity.getMessage(), entity.getType().name(),
                entity.getStatus().name(), entity.getLeadId(), entity.getCreatedAt());
    }

    public record Created(SiteRequestResponse request, UUID leadId) {
    }
}
