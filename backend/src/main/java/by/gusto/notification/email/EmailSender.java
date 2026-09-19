package by.gusto.notification.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Отправка HTML-писем через SMTP (S33). Без настроенного хоста интеграция
 * выключена (send() кидает исключение — канал решает: пропуск или ретрай).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSender {

    private final EmailProperties properties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public void sendHtml(String to, String subject, String html) {
        if (!isEnabled()) {
            throw new IllegalStateException("SMTP не настроен (EMAIL_HOST пуст)");
        }
        JavaMailSender sender = mailSenderProvider.getObject();
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            // S40: адрес получателя — PII, в логи попадает замаскированным
            log.info("EMAIL отправлено: to={}, subject={}", maskEmail(to), subject);
        } catch (Exception e) {
            throw new IllegalStateException("Сбой отправки письма: " + e.getMessage(), e);
        }
    }

    /** ab****@domain.by: локальная часть остаётся одним символом + маска. */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        return (local.isEmpty() ? "*" : local.substring(0, 1)) + "****" + email.substring(at);
    }
}
