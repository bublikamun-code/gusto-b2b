package by.gusto.auth.controller;

import by.gusto.auth.config.SecurityProperties;
import by.gusto.auth.dto.LoginRequest;
import by.gusto.auth.dto.PasswordResetConfirmRequest;
import by.gusto.auth.dto.PasswordResetRequest;
import by.gusto.auth.dto.RegisterRequest;
import by.gusto.auth.dto.TokenResponse;
import by.gusto.auth.dto.UserResponse;
import by.gusto.auth.entity.User;
import by.gusto.auth.mapper.UserMapper;
import by.gusto.auth.service.AuthService;
import by.gusto.auth.service.AuthService.AuthResult;
import by.gusto.auth.service.RateLimitService;
import by.gusto.common.api.ApiResponse;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final SecurityProperties securityProperties;
    private final UserMapper userMapper;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        checkRateLimit("login", httpRequest, request.getEmail());
        AuthResult result = authService.login(request, clientIp(httpRequest), userAgent(httpRequest));
        setRefreshCookie(httpResponse, result.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(result.tokenResponse()));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        checkRateLimit("register", httpRequest, request.getEmail());
        UserResponse user = authService.register(request);
        return ResponseEntity.status(201).body(ApiResponse.success(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String refreshToken = readRefreshCookie(httpRequest);
        AuthResult result = authService.refresh(refreshToken, clientIp(httpRequest), userAgent(httpRequest));
        setRefreshCookie(httpResponse, result.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(result.tokenResponse()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        authService.logout(readRefreshCookie(httpRequest));
        clearRefreshCookie(httpResponse);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        UserResponse user = authService.getCurrentUser(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<Map<String, String>>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest) {
        checkRateLimit("password-reset", httpRequest, request.getEmail());
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Если email существует, инструкции отправлены")));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest httpRequest) {
        checkRateLimit("password-reset", httpRequest, null);
        authService.confirmPasswordReset(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private void checkRateLimit(String endpoint, HttpServletRequest request, String email) {
        String ip = clientIp(request);
        if (!rateLimitService.isAllowed(endpoint, ip, email)) {
            throw new GustoException(ErrorCode.RATE_LIMITED);
        }
        rateLimitService.recordAttempt(endpoint, ip, email);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(securityProperties.getRefreshCookieName(), refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth/refresh")
                .maxAge(Duration.ofDays(7))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(securityProperties.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth/refresh")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = securityProperties.getRefreshCookieName();
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }
}
