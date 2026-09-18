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
    AUTH_EMAIL_NOT_CONFIRMED("AUTH_EMAIL_NOT_CONFIRMED", "Email не подтверждён. Перейдите по ссылке из письма."),
    AUTH_EMAIL_CONFIRM_TOKEN_INVALID("AUTH_EMAIL_CONFIRM_TOKEN_INVALID", "Ссылка подтверждения недействительна или истекла"),
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

    // Orders
    ORDER_STATUS_TRANSITION("ORDER_STATUS_TRANSITION", "Недопустимый переход статуса заказа"),
    ORDER_ALREADY_TAKEN("ORDER_ALREADY_TAKEN", "Заказ уже взят в работу другим менеджером"),

    // Invoices
    INVOICE_INVALID_STATE("INVOICE_INVALID_STATE", "Недопустимый переход статуса счёта"),
    INVOICE_ALREADY_EXISTS("INVOICE_ALREADY_EXISTS", "Активный счёт по этому заказу уже существует"),

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
