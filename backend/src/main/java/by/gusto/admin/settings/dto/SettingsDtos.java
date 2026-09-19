package by.gusto.admin.settings.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * DTO настроек админки (S38). Группы соответствуют разделам страницы
 * «Настройки»: продавец, документы, уведомления, склад, регистрация, витрина.
 * Токен Telegram — write-only: в ответе виден только признак «задан» и
 * источник (settings или .env), само значение не возвращается.
 */
public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record SellerSettings(String name, String unp, String address,
                                 String bankAccount, String bankName, String bankBic) {
    }

    public record DocumentSettings(String seriesTtn, String seriesTn, Double vatDefault) {
    }

    /**
     * @param telegramBotToken write-only: null — не менять, пустая строка —
     *                         убрать, значение — записать в settings
     */
    public record NotificationSettings(String telegramBotToken, boolean telegramTokenSet,
                                       String telegramTokenSource, Map<String, Boolean> rules) {
    }

    public record StockSettings(String defaultLocationId) {
    }

    public record LocationOption(String id, String name) {
    }

    public record AuthSettings(Boolean requireEmailConfirmation) {
    }

    public record LandingSettings(HeroSettings hero, DeliverySettings delivery) {
    }

    public record HeroSettings(String eyebrow, String title, String text) {
    }

    public record DeliverySettings(String title, JsonNode steps) {
    }

    public record SettingsResponse(SellerSettings seller, DocumentSettings documents,
                                   NotificationSettings notifications, StockSettings stock,
                                   AuthSettings auth, LandingSettings landing,
                                   java.util.List<LocationOption> locations) {
    }
}
