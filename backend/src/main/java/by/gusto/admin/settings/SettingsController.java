package by.gusto.admin.settings;

import by.gusto.admin.settings.dto.SettingsDtos.AuthSettings;
import by.gusto.admin.settings.dto.SettingsDtos.DocumentSettings;
import by.gusto.admin.settings.dto.SettingsDtos.LandingSettings;
import by.gusto.admin.settings.dto.SettingsDtos.NotificationSettings;
import by.gusto.admin.settings.dto.SettingsDtos.SellerSettings;
import by.gusto.admin.settings.dto.SettingsDtos.SettingsResponse;
import by.gusto.admin.settings.dto.SettingsDtos.StockSettings;
import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Настройки операционного центра (S38): только ADMIN (матрица 2.1).
 * Чтение — одна сводка по группам; запись — по группам, каждое изменение
 * попадает в audit_log.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {

    private final AdminSettingsService settingsService;
    private final AuthContext authContext;

    @GetMapping
    public ResponseEntity<ApiResponse<SettingsResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.get()));
    }

    @PutMapping("/seller")
    public ResponseEntity<ApiResponse<SettingsResponse>> updateSeller(
            @RequestBody SellerSettings request) {
        settingsService.updateSeller(authContext.getCurrentUser(), request);
        return ResponseEntity.ok(ApiResponse.success(settingsService.get()));
    }

    @PutMapping("/documents")
    public ResponseEntity<ApiResponse<SettingsResponse>> updateDocuments(
            @RequestBody DocumentSettings request) {
        settingsService.updateDocuments(authContext.getCurrentUser(), request);
        return ResponseEntity.ok(ApiResponse.success(settingsService.get()));
    }

    @PutMapping("/notifications")
    public ResponseEntity<ApiResponse<SettingsResponse>> updateNotifications(
            @RequestBody NotificationSettings request) {
        settingsService.updateNotifications(authContext.getCurrentUser(), request);
        return ResponseEntity.ok(ApiResponse.success(settingsService.get()));
    }

    @PutMapping("/stock")
    public ResponseEntity<ApiResponse<SettingsResponse>> updateStock(
            @RequestBody StockSettings request) {
        settingsService.updateStock(authContext.getCurrentUser(), request);
        return ResponseEntity.ok(ApiResponse.success(settingsService.get()));
    }

    @PutMapping("/auth")
    public ResponseEntity<ApiResponse<SettingsResponse>> updateAuth(
            @RequestBody AuthSettings request) {
        settingsService.updateAuth(authContext.getCurrentUser(), request);
        return ResponseEntity.ok(ApiResponse.success(settingsService.get()));
    }

    @PutMapping("/landing")
    public ResponseEntity<ApiResponse<SettingsResponse>> updateLanding(
            @RequestBody LandingSettings request) {
        User actor = authContext.getCurrentUser();
        settingsService.updateLanding(actor, request);
        return ResponseEntity.ok(ApiResponse.success(settingsService.get()));
    }
}
