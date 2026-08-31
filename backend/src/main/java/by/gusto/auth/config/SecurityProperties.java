package by.gusto.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.security")
@Data
public class SecurityProperties {

    private String refreshCookieName;
    private RateLimit rateLimit;

    @Data
    public static class RateLimit {
        private int attempts;
        private long windowMinutes;
    }
}
