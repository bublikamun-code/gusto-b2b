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
    public static final String SELLER_REQUISITES = "seller.requisites";
    public static final String SERIES_TTN = "document.series.ttn";
    public static final String SERIES_TN = "document.series.tn";
    public static final String VAT_DEFAULT = "vat.default";
    public static final String DEFAULT_LOCATION = "stock.default_location";
    public static final String TELEGRAM_BOT_TOKEN = "telegram.bot_token";
    public static final String NOTIFICATION_RULES = "notifications.rules";
    public static final String LANDING_HERO = "landing.hero";
    public static final String LANDING_DELIVERY = "landing.delivery";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** Запись настройки (upsert). value — уже JSON-текст (скаляр или объект). */
    @Transactional
    public void setValue(String key, String jsonValue) {
        jdbcTemplate.update(
                "insert into settings (key, value) values (?, ?::jsonb) "
                        + "on conflict (key) do update set value = excluded.value",
                key, jsonValue);
    }

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

    /** Скаляр (строка/число/boolean) как текст, null если ключа нет или там объект. */
    @Transactional(readOnly = true)
    public Optional<String> getScalar(String key) {
        String json = getString(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.isValueNode() ? Optional.of(node.asText()) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Объектная настройка; null, если ключа нет или значение не объект. */
    @Transactional(readOnly = true)
    public JsonNode getObject(String key) {
        String json = getString(key);
        if (json == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Правило уведомлений (2.6): false только если ключ notifications.rules явно выключает тип. */
    @Transactional(readOnly = true)
    public boolean notificationEnabled(String eventType) {
        JsonNode rules = getObject(NOTIFICATION_RULES);
        return rules == null || !rules.has(eventType) || !rules.get(eventType).asBoolean(true);
    }
}
