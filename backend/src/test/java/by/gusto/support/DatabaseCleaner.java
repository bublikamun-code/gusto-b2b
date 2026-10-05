package by.gusto.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Чистка БД перед интеграционным тестом.
 *
 * Раньше каждый тест выстраивал собственную цепочку {@code repository.deleteAll()}
 * в порядке зависимостей. Цепочка молча ломалась, как только новая сид-миграция
 * добавляла строки в таблицы, на которые ссылались каскадом: тесты удаляли
 * {@code products}, а в order_items на них висел заказ из демо-данных —
 * падало 25 тестов с DataIntegrityViolation.
 *
 * Порядок зависимостей здесь знать не нужно — TRUNCATE ... CASCADE снимает
 * указанные таблицы вместе со всеми, кто на них ссылается.
 *
 * <p>Мастер-данные (каталог, справочники, настройки) НЕ трогаем: их заводят
 * базовые сид-миграции V2/V6/V22, и многие тесты берут из них товар и прайс
 * через {@code findFirst()}. Чистится только то, что заводит сам тест и
 * операционные сид-миграции (V25 демо-данные, V26 страницы CMS).
 */
@Component
public class DatabaseCleaner {

    /**
     * Таблицы, которые тесты считают заранее заполненными.
     *
     * <p>Важно про CASCADE: он снимает не только саму таблицу, но и все таблицы,
     * которые на неё ссылаются. Поэтому в KEEP недостаточно перечислить нужные
     * таблицы — надо ещё те, <em>на которые</em> они ссылаются. {@code users}
     * стоит ссылкой на {@code companies}, так что очистка компаний молча уносила
     * за собой и админа из V2, на которого логинится половина тестов.
     */
    private static final Set<String> KEEP = Set.of(
            "products",
            "categories",
            "brands",
            "product_prices",
            "price_lists",
            "recipes",
            "settings",
            "stock_locations",
            // V9 заводит остатки на базовый каталог и приход в журнал движений;
            // тесты (Stock, Transfer, Crm, Export, Import) читают именно эти строки
            "stock_balances",
            "stock_movements",
            // V2 сеет админа, под которым половина тестов логинится;
            // companies держим, иначе каскад уносит users вместе с админом
            "users",
            "companies",
            "flyway_schema_history");

    private final JdbcTemplate jdbc;

    @Autowired
    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Снимает рабочие данные, оставляя мастер-данные и историю миграций. */
    public void clean() {
        // users.company_id и companies.manager_id ссылаются друг на друга — цикл,
        // который нельзя разорвать удалением (нужно сначала занулить обе ссылки).
        // Пока эти строки остаются после чистки, они блокируют тесты, которые
        // заводят и удаляют собственных пользователей и компании.
        jdbc.update("UPDATE companies SET manager_id = NULL WHERE manager_id IS NOT NULL");
        jdbc.update("UPDATE users SET company_id = NULL WHERE company_id IS NOT NULL");

        // Список исключений подставляем литералами: у Postgres с биндингом
        // «tablename <> ALL (?)» не выводится тип параметра и запрос падает.
        String keepList = KEEP.stream()
                .map(name -> "'" + name + "'")
                .collect(Collectors.joining(", "));
        jdbc.execute(
                """
                DO $$
                DECLARE
                    t TEXT;
                BEGIN
                    FOR t IN
                        SELECT tablename FROM pg_tables
                        WHERE schemaname = 'public'
                          AND tablename <> ALL (ARRAY[%s])
                    LOOP
                        EXECUTE 'TRUNCATE TABLE ' || quote_ident(t) || ' RESTART IDENTITY CASCADE';
                    END LOOP;
                END $$;
                """
                        .formatted(keepList));
    }
}