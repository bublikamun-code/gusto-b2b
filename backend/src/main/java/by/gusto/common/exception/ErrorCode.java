package by.gusto.common.exception;

public enum ErrorCode {
    // Auth
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "Неверный email или пароль"),
    AUTH_2FA_REQUIRED("AUTH_2FA_REQUIRED", "Требуется код двухфакторной аутентификации"),
    AUTH_2FA_INVALID("AUTH_2FA_INVALID", "Неверный код двухфакторной аутентификации"),
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "Access-токен истёк"),
    AUTH_TOKEN_INVALID("AUTH_TOKEN_INVALID", "Токен недействителен"),
    AUTH_REFRESH_INVALID("AUTH_REFRESH_INVALID", "Refresh-токен недействителен"),
    AUTH_REFRESH_REUSED("AUTH_REFRESH_REUSED", "Refresh-токен уже использован. Выполните повторный вход."),
    AUTH_RESET_TOKEN_INVALID("AUTH_RESET_TOKEN_INVALID", "Ссылка для восстановления пароля недействительна или истекла"),
    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "Требуется авторизация"),

    // Access / business
    ACCESS_DENIED("ACCESS_DENIED", "Доступ запрещён"),
    NOT_FOUND("NOT_FOUND", "Не найдено"),
    CONFLICT("CONFLICT", "Конфликт данных"),
    RATE_LIMITED("RATE_LIMITED", "Слишком много попыток. Попробуйте позже."),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "Конфликт идемпотентного ключа"),

    // Stock
    STOCK_INSUFFICIENT("STOCK_INSUFFICIENT", "Недостаточно остатка"),
    STOCK_DOCUMENT_INVALID("STOCK_DOCUMENT_INVALID", "Неверный переход статуса документа"),

    // Validation
    VALIDATION_FAILED("VALIDATION_FAILED", "Ошибка валидации запроса"),

    // Generic
    INTERNAL("INTERNAL", "Внутренняя ошибка сервера");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
