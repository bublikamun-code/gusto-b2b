package by.gusto.request.controller;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.request.dto.SiteRequestDtos.SiteRequestResponse;
import by.gusto.request.entity.SiteRequestEntity;
import by.gusto.request.service.SiteRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Заявки с сайта в CRM (S31): список со фильтром статуса и смена статуса
 * NEW→IN_PROGRESS→CLOSED. Права — MANAGER/ADMIN (матрица 2.1).
 */
@RestController
@RequestMapping("/api/v1/crm/site-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class ManagerSiteRequestController {

    private final SiteRequestService siteRequestService;
    private final AuthContext authContext;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SiteRequestResponse>>> list(
            @RequestParam(required = false) SiteRequestEntity.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SiteRequestResponse> result =
                siteRequestService.list(authContext.getCurrentUser(), status, page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent()));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SiteRequestResponse>> changeStatus(
            @PathVariable UUID id,
            @RequestParam SiteRequestEntity.Status status) {
        return ResponseEntity.ok(ApiResponse.success(
                siteRequestService.changeStatus(id, status, authContext.getCurrentUser())));
    }
}
