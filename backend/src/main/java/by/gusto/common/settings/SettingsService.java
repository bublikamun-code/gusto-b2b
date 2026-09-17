package by.gusto.common.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Чтение ключей из таблицы settings (value — JSONB).
 * Значения либо скаляры ('false', '10', '"A"'), либо объекты (реквизиты).
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    public static final String REQUIRE_EMAIL_CONFIRMATION = "auth.require_email_confirmation";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        String json = getString(key);
        if (json == null) {
            return defaultValue;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.isBoolean() ? node.asBoolean() : Boolean.parseBoolean(node.asText());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public String getString(String key) {
        var values = jdbcTemplate.queryForList(
                "select value::text from settings where key = ?", String.class, key);
        return values.isEmpty() ? null : values.get(0);
    }

    /** Скалярная строка из JSONB (кавычки снимаются). */
    @Transactional(readOnly = true)
    public Optional<UUID> getUuid(String key) {
        String json = getString(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(objectMapper.readTree(json).asText()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
