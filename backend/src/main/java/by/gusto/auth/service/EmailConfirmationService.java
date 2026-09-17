package by.gusto.auth.service;

import by.gusto.auth.entity.EmailConfirmationToken;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.EmailConfirmationTokenRepository;
import by.gusto.auth.repository.UserRepository;
import by.gusto.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Подтверждение email при саморегистрации физлица (S08.1).
 * Токен хранится хэшем (SHA-256), TTL 24 ч; письмо со ссылкой уходит через outbox.
 */
@Service
@RequiredArgsConstructor
public class EmailConfirmationService {

    private static final int TOKEN_LENGTH = 64;
    private static final long TTL_HOURS = 24;

    private final EmailConfirmationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final OutboxService outboxService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Transactional
    public void sendConfirmation(User user) {
        String raw = RandomStringUtils.secure().nextAlphanumeric(TOKEN_LENGTH);
        EmailConfirmationToken token = EmailConfirmationToken.builder()
                .user(user)
                .tokenHash(sha256(raw))
                .expiresAt(Instant.now().plus(TTL_HOURS, ChronoUnit.HOURS))
                .used(false)
                .build();
        tokenRepository.save(token);
        outboxService.append("user", user.getId(), OutboxService.TYPE_EMAIL_CONFIRMATION, Map.of(
                "to", user.getEmail(),
                "subject", "Подтверждение email — Густо",
                "confirmationUrl", appBaseUrl + "/confirm-email?token=" + raw
        ));
    }

    /**
     * Повторная отправка: без раскрытия факта существования адреса.
     * Письмо уходит только физлицам с неподтверждённым email.
     */
    @Transactional
    public void resendByEmail(String email) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        if (user.getRole() != Role.CUSTOMER_INDIVIDUAL || user.getEmailConfirmedAt() != null) {
            return;
        }
        sendConfirmation(user);
    }

    @Transactional
    public Optional<User> confirm(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<EmailConfirmationToken> tokenOpt = tokenRepository.findByTokenHash(sha256(rawToken));
        if (tokenOpt.isEmpty() || tokenOpt.get().isUsed() || tokenOpt.get().getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        EmailConfirmationToken token = tokenOpt.get();
        token.setUsed(true);
        User user = token.getUser();
        user.setEmailConfirmedAt(Instant.now());
        tokenRepository.save(token);
        userRepository.save(user);
        return Optional.of(user);
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
