package by.gusto.auth.service;

import by.gusto.auth.config.JwtProperties;
import by.gusto.auth.entity.RefreshToken;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.RefreshTokenRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    private static final int RAW_TOKEN_LENGTH = 64;

    @Transactional
    public TokenPair create(User user, String ip, String userAgent) {
        revokeAllUserTokens(user);
        String raw = RandomStringUtils.secure().nextAlphanumeric(RAW_TOKEN_LENGTH);
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(raw))
                .ip(ip)
                .userAgent(userAgent)
                .expiresAt(Instant.now().plus(jwtProperties.getRefreshTtlDays(), ChronoUnit.DAYS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(token);
        return new TokenPair(raw, token);
    }

    @Transactional
    public Optional<TokenPair> rotate(String rawToken, String ip, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String hash = sha256(rawToken);
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash)
                .orElse(null);

        if (current == null || current.getRevoked() || current.getExpiresAt().isBefore(Instant.now())) {
            // Reuse detection: token hash exists but token is invalid → possible replay attack
            if (current != null) {
                revokeAllUserTokens(current.getUser());
                throw new GustoException(ErrorCode.AUTH_REFRESH_REUSED);
            }
            return Optional.empty();
        }

        User user = current.getUser();
        current.setRevoked(true);
        refreshTokenRepository.save(current);

        String newRaw = RandomStringUtils.secure().nextAlphanumeric(RAW_TOKEN_LENGTH);
        RefreshToken next = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(newRaw))
                .ip(ip)
                .userAgent(userAgent)
                .expiresAt(Instant.now().plus(jwtProperties.getRefreshTtlDays(), ChronoUnit.DAYS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(next);
        return Optional.of(new TokenPair(newRaw, next));
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public int revokeAllUserTokens(User user) {
        return refreshTokenRepository.revokeAllByUser(user);
    }

    @Transactional
    public int cleanupExpired() {
        return refreshTokenRepository.revokeExpired(Instant.now());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record TokenPair(String rawToken, RefreshToken entity) {
    }
}
