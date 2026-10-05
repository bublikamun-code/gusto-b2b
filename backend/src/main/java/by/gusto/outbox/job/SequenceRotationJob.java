package by.gusto.outbox.job;

import by.gusto.common.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;

/**
 * Ротация документных sequences на новый год (S31, 2.2): при первом запуске
 * дня гарантирует существование sequence текущего года для счетов и накладных
 * (серии — из settings). Создание идемпотентно (IF NOT EXISTS).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SequenceRotationJob {

    private final JdbcTemplate jdbcTemplate;
    private final SettingsService settingsService;

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void rotate() {
        int year = Year.now().getValue();

        // Раньше ротировались только invoice/tn/ttn, а заказы (order_seq_<year>),
        // закупки (doc_seq_purchase_<year>) и пять складских (doc_seq_warehouse_*_<year>)
        // создавались миграциями строго на 2026 год — с 01.01.2027 их не существовало,
        // и создание заказа/закупки/приёмки падало в 500 (аудит 2026-09-30, P1-20).
        ensureSequence("order_seq_" + year);
        ensureSequence("doc_seq_purchase_" + year);
        for (String kind : List.of("incoming", "outgoing", "write_off", "transfer", "inventory")) {
            ensureSequence("doc_seq_warehouse_" + kind + "_" + year);
        }

        ensureSequence("doc_seq_invoice_" + year);

        for (String prefix : List.of("tn", "ttn")) {
            String key = "document.series." + prefix;
            String series = seriesOf(key);
            if (series != null) {
                ensureSequence("doc_seq_" + prefix + "_" + series + "_" + year);
            }
        }
    }

    private String seriesOf(String key) {
        String json = settingsService.getString(key);
        if (json == null) {
            return null;
        }
        try {
            String parsed = json.replace("\"", "").trim();
            return parsed.matches("[A-Za-zА-Яа-яЁё0-9]{1,10}") ? parsed.toUpperCase() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void ensureSequence(String name) {
        try {
            jdbcTemplate.execute("create sequence if not exists \"" + name + "\"");
        } catch (Exception e) {
            log.warn("ROTATION: не удалось создать sequence {}: {}", name, e.getMessage());
        }
    }
}
