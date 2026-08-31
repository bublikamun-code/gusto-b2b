package by.gusto.auth.service;

import by.gusto.auth.entity.PasswordResetToken;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.PasswordResetTokenRepository;
import by.gusto.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
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
public class PasswordResetService {

    private static final int TOKEN_LENGTH = 64;
    private static final long TTL_MINUTES = 30;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public String createToken(String email) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            // Не раскрываем наличие email
            return null;
        }
        User user = userOpt.get();
        String raw = RandomStringUtils.secure().nextAlphanumeric(TOKEN_LENGTH);
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash(sha256(raw))
                .expiresAt(Instant.now().plus(TTL_MINUTES, ChronoUnit.MINUTES))
                .used(false)
                .build();
        tokenRepository.save(token);
        return raw;
    }

    @Transactional
    public boolean resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        String hash = sha256(rawToken);
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByTokenHash(hash);
        if (tokenOpt.isEmpty() || tokenOpt.get().getUsed() || tokenOpt.get().getExpiresAt().isBefore(Instant.now())) {
            return false;
        }
        PasswordResetToken token = tokenOpt.get();
        token.setUsed(true);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        tokenRepository.save(token);
        userRepository.save(user);
        return true;
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
}
