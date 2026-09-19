package by.gusto.admin.settings;

import by.gusto.admin.settings.dto.SettingsDtos.AuthSettings;
import by.gusto.admin.settings.dto.SettingsDtos.DeliverySettings;
import by.gusto.admin.settings.dto.SettingsDtos.DocumentSettings;
import by.gusto.admin.settings.dto.SettingsDtos.HeroSettings;
import by.gusto.admin.settings.dto.SettingsDtos.LandingSettings;
import by.gusto.admin.settings.dto.SettingsDtos.LocationOption;
import by.gusto.admin.settings.dto.SettingsDtos.NotificationSettings;
import by.gusto.admin.settings.dto.SettingsDtos.SellerSettings;
import by.gusto.admin.settings.dto.SettingsDtos.SettingsResponse;
import by.gusto.admin.settings.dto.SettingsDtos.StockSettings;
import by.gusto.audit.AuditService;
import by.gusto.auth.entity.User;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.common.settings.SettingsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Настройки операционного центра (S38): реквизиты продавца, серии документов,
 * НДС, Telegram и правила уведомлений (2.6), склад по умолчанию (1.6), гейт
 * подтверждения email (S08.1), тексты лендинга. SMTP остаётся в .env (S33).
 * Каждое изменение пишется в audit_log; токен в лог не попадает.
 */
@Service
@RequiredArgsConstructor
public class AdminSettingsService {

    /** Типы событий Telegram-канала (S32) — единственные управляемые правила. */
    private static final List<String> RULE_EVENTS = List.of(
            "ORDER_CREATED", "ORDER_STATUS_CHANGED", "INVOICE_ISSUED", "SITE_REQUEST_CREATED");

    private final SettingsService settingsService;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        return new SettingsResponse(
                seller(),
                documents(),
                notifications(),
                stock(),
                auth(),
                landing(),
                locations());
    }

    @Transactional
    public void updateSeller(User actor, SellerSettings request) {
        requireText(request.name(), "Название организации обязательно");
        requireText(request.unp(), "УНП обязателен");
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", request.name().trim());
        value.put("unp", request.unp().trim());
        value.put("address", blankToEmpty(request.address()));
        value.put("bank_account", blankToEmpty(request.bankAccount()));
        value.put("bank_name", blankToEmpty(request.bankName()));
        value.put("bank_bic", blankToEmpty(request.bankBic()));
        writeObject(actor, SettingsService.SELLER_REQUISITES, value, "seller.requisites");
    }

    @Transactional
    public void updateDocuments(User actor, DocumentSettings request) {
        requireText(request.seriesTtn(), "Серия ТТН обязательна");
        requireText(request.seriesTn(), "Серия ТН обязательна");
        if (request.vatDefault() == null || request.vatDefault() < 0 || request.vatDefault() > 25) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "НДС по умолчанию — число от 0 до 25");
        }
        Map<String, String> before = new LinkedHashMap<>();
        before.put(SettingsService.SERIES_TTN, scalar(SettingsService.SERIES_TTN));
        before.put(SettingsService.SERIES_TN, scalar(SettingsService.SERIES_TN));
        before.put(SettingsService.VAT_DEFAULT, scalar(SettingsService.VAT_DEFAULT));
        settingsService.setValue(SettingsService.SERIES_TTN, quote(request.seriesTtn().trim()));
        settingsService.setValue(SettingsService.SERIES_TN, quote(request.seriesTn().trim()));
        settingsService.setValue(SettingsService.VAT_DEFAULT, request.vatDefault().toString());
        auditService.append(actor.getId(), "SETTINGS_UPDATE", "settings", null,
                Map.of("group", "documents", "before", before), Map.of(
                        "group", "documents",
                        SettingsService.SERIES_TTN, request.seriesTtn().trim(),
                        SettingsService.SERIES_TN, request.seriesTn().trim(),
                        SettingsService.VAT_DEFAULT, request.vatDefault()));
    }

    @Transactional
    public void updateNotifications(User actor, NotificationSettings request) {
        Map<String, Object> after = new LinkedHashMap<>();
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("telegramTokenSet", telegramTokenSet());
        if (request.telegramBotToken() != null) {
            String token = request.telegramBotToken().trim();
            before.put(SettingsService.TELEGRAM_BOT_TOKEN, "present");
            if (token.isEmpty()) {
                jdbcTemplate.update("delete from settings where key = ?",
                        SettingsService.TELEGRAM_BOT_TOKEN);
                after.put(SettingsService.TELEGRAM_BOT_TOKEN, "cleared");
            } else {
                settingsService.setValue(SettingsService.TELEGRAM_BOT_TOKEN, quote(token));
                after.put(SettingsService.TELEGRAM_BOT_TOKEN, "present");
            }
        }
        Map<String, Boolean> rules = new LinkedHashMap<>();
        for (String event : RULE_EVENTS) {
            rules.put(event, request.rules() == null || request.rules().get(event) != Boolean.FALSE);
        }
        before.put(SettingsService.NOTIFICATION_RULES, String.valueOf(settingsService.getObject(
                SettingsService.NOTIFICATION_RULES)));
        settingsService.setValue(SettingsService.NOTIFICATION_RULES, toJson(rules));
        after.put(SettingsService.NOTIFICATION_RULES, rules);
        auditService.append(actor.getId(), "SETTINGS_UPDATE", "settings", null,
                Map.of("group", "notifications", "before", before),
                Map.of("group", "notifications", "after", after));
    }

    @Transactional
    public void updateStock(User actor, StockSettings request) {
        requireText(request.defaultLocationId(), "Склад по умолчанию обязателен");
        UUID locationId;
        try {
            locationId = UUID.fromString(request.defaultLocationId());
        } catch (IllegalArgumentException e) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Некорректный идентификатор склада");
        }
        List<String> found = jdbcTemplate.queryForList(
                "select name from stock_locations where id = ?", String.class, locationId);
        if (found.isEmpty()) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Склад не найден");
        }
        String before = scalar(SettingsService.DEFAULT_LOCATION);
        settingsService.setValue(SettingsService.DEFAULT_LOCATION, quote(locationId.toString()));
        auditService.append(actor.getId(), "SETTINGS_UPDATE", "settings", null,
                Map.of("group", "stock", "before", Map.of(SettingsService.DEFAULT_LOCATION, before)),
                Map.of("group", "stock",
                        SettingsService.DEFAULT_LOCATION, locationId.toString(),
                        "locationName", found.get(0)));
    }

    @Transactional
    public void updateAuth(User actor, AuthSettings request) {
        if (request.requireEmailConfirmation() == null) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Не передан флаг requireEmailConfirmation");
        }
        boolean before = settingsService.getBoolean(SettingsService.REQUIRE_EMAIL_CONFIRMATION, false);
        settingsService.setValue(SettingsService.REQUIRE_EMAIL_CONFIRMATION,
                String.valueOf(request.requireEmailConfirmation()));
        auditService.append(actor.getId(), "SETTINGS_UPDATE", "settings", null,
                Map.of("group", "auth", "before", Map.of(
                        SettingsService.REQUIRE_EMAIL_CONFIRMATION, before)),
                Map.of("group", "auth",
                        SettingsService.REQUIRE_EMAIL_CONFIRMATION, request.requireEmailConfirmation()));
    }

    @Transactional
    public void updateLanding(User actor, LandingSettings request) {
        if (request.hero() != null) {
            Map<String, Object> hero = new LinkedHashMap<>();
            hero.put("eyebrow", blankToEmpty(request.hero().eyebrow()));
            hero.put("title", blankToEmpty(request.hero().title()));
            hero.put("text", blankToEmpty(request.hero().text()));
            writeObject(actor, SettingsService.LANDING_HERO, hero, "landing.hero");
        }
        if (request.delivery() != null) {
            JsonNode steps = request.delivery().steps();
            if (steps != null && !steps.isArray()) {
                throw new GustoException(ErrorCode.VALIDATION_FAILED,
                        "steps должен быть массивом шагов доставки");
            }
            Map<String, Object> delivery = new LinkedHashMap<>();
            delivery.put("title", blankToEmpty(request.delivery().title()));
            delivery.put("steps", steps);
            writeObject(actor, SettingsService.LANDING_DELIVERY, delivery, "landing.delivery");
        }
    }

    // ----- чтение ----------------------------------------------------------------

    private SellerSettings seller() {
        JsonNode node = settingsService.getObject(SettingsService.SELLER_REQUISITES);
        if (node == null) {
            return new SellerSettings("", "", "", "", "", "");
        }
        return new SellerSettings(
                text(node, "name"), text(node, "unp"), text(node, "address"),
                text(node, "bank_account"), text(node, "bank_name"), text(node, "bank_bic"));
    }

    private DocumentSettings documents() {
        return new DocumentSettings(
                scalar(SettingsService.SERIES_TTN),
                scalar(SettingsService.SERIES_TN),
                scalar(SettingsService.VAT_DEFAULT) == null
                        ? null : Double.valueOf(scalar(SettingsService.VAT_DEFAULT)));
    }

    private NotificationSettings notifications() {
        JsonNode rules = settingsService.getObject(SettingsService.NOTIFICATION_RULES);
        Map<String, Boolean> ruleMap = new LinkedHashMap<>();
        for (String event : RULE_EVENTS) {
            ruleMap.put(event, rules == null || !rules.has(event) || rules.get(event).asBoolean(true));
        }
        boolean settingsToken = settingsService.getString(SettingsService.TELEGRAM_BOT_TOKEN) != null;
        return new NotificationSettings(null, telegramTokenSet(),
                settingsToken ? "settings" : "env", ruleMap);
    }

    private StockSettings stock() {
        return new StockSettings(scalar(SettingsService.DEFAULT_LOCATION));
    }

    private AuthSettings auth() {
        return new AuthSettings(settingsService.getBoolean(
                SettingsService.REQUIRE_EMAIL_CONFIRMATION, false));
    }

    private LandingSettings landing() {
        JsonNode hero = settingsService.getObject(SettingsService.LANDING_HERO);
        JsonNode delivery = settingsService.getObject(SettingsService.LANDING_DELIVERY);
        return new LandingSettings(
                hero == null ? null : new HeroSettings(
                        text(hero, "eyebrow"), text(hero, "title"), text(hero, "text")),
                delivery == null ? null : new DeliverySettings(
                        text(delivery, "title"), delivery.get("steps")));
    }

    private List<LocationOption> locations() {
        return jdbcTemplate.query(
                "select id, name from stock_locations order by name",
                (rs, i) -> new LocationOption(rs.getString("id"), rs.getString("name")));
    }

    // ----- helpers ---------------------------------------------------------------

    private boolean telegramTokenSet() {
        if (settingsService.getString(SettingsService.TELEGRAM_BOT_TOKEN) != null) {
            return true;
        }
        // .env/compose (S32) остаётся резервным источником
        return envTelegramTokenPresent();
    }

    private boolean envTelegramTokenPresent() {
        return System.getenv("TELEGRAM_BOT_TOKEN") != null
                && !System.getenv("TELEGRAM_BOT_TOKEN").isBlank();
    }

    private void writeObject(User actor, String key, Map<String, Object> value, String label) {
        JsonNode beforeNode = settingsService.getObject(key);
        Map<String, Object> before = beforeNode == null ? null
                : objectMapper.convertValue(beforeNode, new TypeReference<>() { });
        settingsService.setValue(key, toJson(value));
        Map<String, Object> after = new LinkedHashMap<>(value);
        auditService.append(actor.getId(), "SETTINGS_UPDATE", "settings", null,
                before == null ? Map.of(label, "empty") : Map.of("before", before),
                Map.of("after", after));
    }

    private String scalar(String key) {
        return settingsService.getScalar(key).orElse(null);
    }

    private String text(JsonNode node, String field) {
        return node.has(field) && node.get(field).isTextual() ? node.get(field).asText() : "";
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, message);
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new GustoException(ErrorCode.INTERNAL, "Не удалось сериализовать настройку");
        }
    }
}
