package by.gusto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Скелет S02: пока открыты health-эндпоинты и весь API.
 * Закрытие эндпоинтов и JWT — S08/S09 (см. чек-лист).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/healthz", "/readyz", "/actuator/**", "/api/**").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
