package by.gusto.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Единая клампинг параметров постраничной выдачи (аудит 2026-09-30, P2).
 *
 * <p>Раньше в проекте встречалось {@code PageRequest.of(page, Math.min(size, 100))} — с
 * клампом только сверху. {@code ?size=0} и {@code ?page=-1} приводили к
 * {@code IllegalArgumentException}, а generic-обработчик отдавал 500. Для публичного
 * {@code /api/v1/catalog/products} это была ошибка и в логах, и в ответе клиенту
 * (воспроизведено на стенде: HTTP 500 на permitAll-ручке).
 */
public final class Pages {

    public static final int MAX_SIZE = 100;

    private Pages() {
    }

    /** page >= 0, иначе Spring бросает IllegalArgumentException. */
    public static int clampPage(int page) {
        return Math.max(page, 0);
    }

    /** 1 <= size <= MAX_SIZE. */
    public static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }

    public static PageRequest of(int page, int size) {
        return PageRequest.of(clampPage(page), clampSize(size));
    }

    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(clampPage(page), clampSize(size), sort);
    }
}