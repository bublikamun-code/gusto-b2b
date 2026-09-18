package by.gusto.cabinet.controller;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.auth.service.RefreshTokenService;
import by.gusto.common.api.ApiResponse;
import by.gusto.cabinet.dto.ProfileDtos.ChangePasswordRequest;
import by.gusto.cabinet.dto.ProfileDtos.ProfileResponse;
import by.gusto.cabinet.dto.ProfileDtos.UpdateProfileRequest;
import by.gusto.cabinet.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Профиль и смена пароля клиента (S23.1). Роли — юрлицо и физлицо.
 */
@RestController
@RequestMapping("/api/v1/cabinet/profile")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER_LEGAL','CUSTOMER_INDIVIDUAL')")
public class ProfileController {

    private final ProfileService profileService;
    private final AuthContext authContext;
    private final RefreshTokenService refreshTokenService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.success(profileService.getProfile(authContext.getCurrentUser())));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                profileService.updateProfile(authContext.getCurrentUser(), request)));
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        User user = authContext.getCurrentUser();
        profileService.changePassword(user, request);
        // После смены пароля все refresh-токены ревокнуты — текущая сессия тоже
        // (фронтенд разлогинивает и предлагает войти с новым паролем)
        refreshTokenService.revokeAllUserTokens(user);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("message", "Пароль изменён. Войдите с новым паролем.")));
    }
}
