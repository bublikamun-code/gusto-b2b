package by.gusto.outbox.channel;

import by.gusto.outbox.entity.OutboxMessage;

/**
 * Канал доставки outbox-сообщений (1.6). Регистрируются по типам:
 * EMAIL_* — SMTP (S33), TG_* — Telegram (S32), прочее — журналирование.
 * Реестр каналов и ретраи — OutboxPoller (S31).
 */
public interface OutboxChannel {

    /** Префиксы типов сообщений, которые обслуживает канал. */
    boolean supports(String type);

    /** true — доставлено; исключение/false — ретрай по backoff-политике. */
    boolean send(OutboxMessage message);
}
