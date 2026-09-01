package by.gusto.auth.service;

import by.gusto.auth.dto.LoginRequest;
import by.gusto.auth.dto.RegisterRequest;
import by.gusto.auth.dto.TokenResponse;
import by.gusto.auth.dto.UserResponse;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.mapper.UserMapper;
import by.gusto.auth.repository.UserRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final TotpService totpService;
    private final UserMapper userMapper;

    @Transactional
    public AuthResult login(LoginRequest request, String ip, String userAgent) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new GustoException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        org.springframework.security.core.userdetails.User principal =
                (org.springframework.security.core.userdetails.User) authentication.getPrincipal();

        User user = userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new GustoException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (user.isTotpEnabled()) {
            String totpCode = request.getTotpCode();
            if (totpCode == null || totpCode.isBlank()) {
                throw new GustoException(ErrorCode.AUTH_2FA_REQUIRED);
            }
            if (!totpService.verifyCode(user, totpCode) && !totpService.verifyRecoveryCode(user, totpCode)) {
                throw new GustoException(ErrorCode.AUTH_2FA_INVALID);
            }
        }

        String accessToken = jwtService.generateAccessToken(user);
        RefreshTokenService.TokenPair refreshPair = refreshTokenService.create(user, ip, userAgent);

        TokenResponse response = TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTtlSeconds())
                .build();
        UserResponse userResponse = userMapper.toResponse(user);
        return new AuthResult(response, refreshPair.rawToken(), userResponse);
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new GustoException(ErrorCode.CONFLICT, "Пользователь с таким email уже существует");
        }
        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CUSTOMER_INDIVIDUAL);
        user.setActive(true);
        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken, String ip, String userAgent) {
        RefreshTokenService.TokenPair pair = refreshTokenService.rotate(rawRefreshToken, ip, userAgent)
                .orElseThrow(() -> new GustoException(ErrorCode.AUTH_REFRESH_INVALID));
        User user = pair.entity().getUser();
        String accessToken = jwtService.generateAccessToken(user);
        TokenResponse response = TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTtlSeconds())
                .build();
        return new AuthResult(response, pair.rawToken(), null);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    public String requestPasswordReset(String email) {
        return passwordResetService.createToken(email);
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        User user = passwordResetService.resetPassword(token, newPassword)
                .orElseThrow(() -> new GustoException(ErrorCode.AUTH_RESET_TOKEN_INVALID));
        refreshTokenService.revokeAllUserTokens(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new GustoException(ErrorCode.AUTH_UNAUTHORIZED));
        return userMapper.toResponse(user);
    }

    public record AuthResult(TokenResponse tokenResponse, String refreshToken, UserResponse userResponse) {
    }
}
