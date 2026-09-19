package by.gusto.notification.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SMTP-транспорт (S33): доступы только в .env (в админке не правятся — S38);
 * правила получателей и шаблоны — в коде/settings. Пустой host — интеграция
 * выключена: email-события пропускаются без ошибок.
 */
@Component
@Data
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    private String host = "";
    private int port = 587;
    private String username = "";
    private String password = "";
    private String from = "no-reply@gustomeat.by";

    public boolean isEnabled() {
        return host != null && !host.isBlank();
    }
}
