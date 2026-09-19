package by.gusto.outbox.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Фоновая очистка (S31, 1.6): просроченные токены подтверждения email,
 * восстановления пароля и идемпотентных ключей (TTL 24 ч).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupJob {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 23 * * * *")
    @Transactional
    public void cleanup() {
        int idempotency = jdbcTemplate.update("delete from idempotency_keys where expires_at < now()");
        int confirmTokens = jdbcTemplate.update(
                "delete from email_confirmation_tokens where expires_at < now()");
        int resetTokens = jdbcTemplate.update(
                "delete from password_reset_tokens where expires_at < now()");
        if (idempotency + confirmTokens + resetTokens > 0) {
            log.info("CLEANUP: idempotency={}, confirmTokens={}, resetTokens={}",
                    idempotency, confirmTokens, resetTokens);
        }
    }
}
