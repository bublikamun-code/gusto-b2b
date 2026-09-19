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

        ensureSequence("doc_seq_invoice_" + year);

        for (String prefix : List.of("tn", "ttn")) {
            String key = "document.series." + prefix;
            String series = seriesOf(key);
            if (series != null) {
                ensureSequence("doc_seq_" + prefix + "_" + series + "_" + year);
            }
        }
        // warehouse-sequences создаются миграциями (V10/V11) на текущий год;
        // ротация остальных типов — по мере появления новых серий в settings
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
