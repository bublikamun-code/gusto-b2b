-- V2: сид-данные (план S05: админ-пользователь, настройки документов)
-- Пароль админа приходит из .env (ADMIN_EMAIL/ADMIN_PASSWORD) через плейсхолдеры
-- Flyway (spring.flyway.placeholders) и хэшируется bcrypt'ом (pgcrypto crypt/gen_salt('bf')).
-- Роли — CHECK-ограничение в users.role (отдельной таблицы нет), сидить нечего.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Администратор (роль ADMIN). Пароль в открытом виде в БД не хранится.
INSERT INTO users (email, password_hash, full_name, role, is_active)
VALUES ('${admin_email}', crypt('${admin_password}', gen_salt('bf')), 'Администратор', 'ADMIN', TRUE);

-- Настройки документов и продавца (план 2.2, 2.3, 2.4; демо-реквизиты из примера ТТН,
-- в проде заменяются в админке — S38)
INSERT INTO settings (key, value) VALUES
    ('document.series.ttn', '"A"'),
    ('document.series.tn',  '"A"'),
    ('vat.default',         '10'),
    ('seller.requisites', '{
        "name": "ЧТУП «ЛорСан»",
        "unp": "191536521",
        "address": "220028 г. Минск, ул. Бородинская, д. 1Б, пом. 14, РБ",
        "bank_account": "",
        "bank_name": "",
        "bank_bic": ""
    }');
