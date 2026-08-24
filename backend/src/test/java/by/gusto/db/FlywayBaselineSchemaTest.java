package by.gusto.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Приёмка S05: миграции V1 (baseline) + V2 (seed) применяются на чистую БД,
 * схема соответствует плану (Часть 3), сиды на месте.
 */
@SpringBootTest
@Testcontainers
class FlywayBaselineSchemaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private JdbcTemplate jdbc;

    private static final List<String> EXPECTED_TABLES = List.of(
            "companies", "users", "refresh_tokens", "files",
            "categories", "brands", "products", "product_images",
            "price_lists", "product_prices", "customer_prices", "customer_discounts",
            "stock_locations", "suppliers",
            "orders", "order_items",
            "purchase_orders", "purchase_order_items",
            "warehouse_documents", "warehouse_document_items", "stock_movements",
            "carts", "cart_items",
            "outbox_messages", "notification_subscriptions",
            "invoices", "invoice_items", "waybills", "waybill_items", "payments",
            "leads", "crm_tasks", "crm_notes",
            "site_requests", "articles", "audit_log", "integration_files", "settings"
    );

    @Test
    void allTablesFromPlanExist() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_name IN ('" +
                        String.join("','", EXPECTED_TABLES) + "')",
                Integer.class);
        assertThat(count).isEqualTo(EXPECTED_TABLES.size());
    }

    @Test
    void extensionsInstalled() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname IN ('citext', 'pg_trgm', 'pgcrypto')",
                Integer.class);
        assertThat(count).isEqualTo(3);
    }

    @Test
    void adminSeededWithBcryptPassword() {
        // bcrypt-хэш от дефолтного пароля 'change-me' (плейсхолдер Flyway, .env в репозиторий не коммитится)
        Boolean matches = jdbc.queryForObject(
                "SELECT password_hash = crypt('change-me', password_hash) FROM users WHERE role = 'ADMIN'",
                Boolean.class);
        assertThat(matches).isTrue();
    }

    @Test
    void documentSettingsSeeded() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM settings WHERE key IN " +
                        "('document.series.ttn', 'document.series.tn', 'vat.default', 'seller.requisites')",
                Integer.class);
        assertThat(count).isEqualTo(4);
    }

    @Test
    void emailIsCitextAndUniqueCaseInsensitive() {
        jdbc.update("INSERT INTO users (email, password_hash, full_name, role) " +
                "VALUES ('Case@Test.by', 'x', 'Тест', 'CUSTOMER_INDIVIDUAL')");
        Integer found = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE email = 'case@test.by'", Integer.class);
        assertThat(found).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, role) " +
                        "VALUES ('CASE@test.by', 'x', 'Дубль', 'CUSTOMER_INDIVIDUAL')"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void roleCheckConstraintRejectsUnknownRole() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, role) " +
                        "VALUES ('bad@test.by', 'x', 'Нет роли', 'SUPERUSER')"))
                .hasMessageContaining("users_role_check");
    }
}
