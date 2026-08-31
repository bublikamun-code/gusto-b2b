# GUSTO B2B — Финальный подробный план реализации (чек-лист)

> Версия 1.3 · 2026-08-24
> v1.3 — команды «перед пушем» в Части 6 унифицированы с 6.3/AGENTS.md (`mvn -B verify`; убраны `./mvnw` — wrapper не используется по ADR — и `npm run test` — Vitest ещё не добавлен); зафиксировано: ветка по умолчанию на GitHub — `develop`; задачи S04 приведены к фактическим Makefile-командам.
> v1.2 — добавлены: синхронизация локального окружения (6.1), правила асинхронной работы (6.2), ритуал AI-ассистента со сверкой файлов (6.3), хранение плана и отметка прогресса в репозитории (6.4); отмечен прогресс S01–S04.
> v1.1 — закрыты пробелы перед стартом: таблицы корзины/outbox/подписок в схеме БД, механизм нумерации через sequences, rate limiting с S08, модель доступа к файлам, идемпотентность заказов, восстановление пароля, саморегистрация физлиц, частичная оплата счетов, зафиксированы бренд-токены и сайт-образец.
> Предназначен для двух разработчиков (Dev A — frontend, Dev B — backend) + AI-ассистенты.
> После КАЖДОЙ сессии: коммит → push → PR → merge в `develop`.

---

# ЧАСТЬ 1. РЕШЕНИЯ ПО АРХИТЕКТУРЕ

## 1.1. Почему НЕ микросервисы

Микросервисная архитектура — это когда каждый модуль (каталог, заказы, счета) живёт в отдельном приложении со своей БД и деплоится отдельно. Проблемы для нашего случая:

1. **Команда из двух человек.** Микросервисы требуют DevOps-нагрузки: оркестрация (Kubernetes), service discovery, распределённые логи, распределённый трейсинг. На это уйдёт 30–40% времени команды.
2. **Распределённые транзакции.** Заказ должен списать остаток, создать счёт и отправить уведомление. В монолите — одна транзакция БД. В микросервисах — saga pattern, компенсации, идемпотентность. Это в разы сложнее и багодольнее.
3. **Тестирование.** Интеграционный тест монолита поднимает одно приложение. Микросервисы — N контейнеров + брокер сообщений.
4. **Локальная разработка.** Монолит: `docker compose up`. Микросервисы: 10+ сервисов, каждый со своим конфигом.
5. **Отладка.** Стек-трейс в монолите сквозной. В микросервисах ошибка гуляет между сервисами.

**Вывод:** строим **модульный монолит** — одно приложение, чёткие границы пакетов (`catalog/`, `orders/`, `invoices/`...). Когда проект вырастет (много разработчиков, высокая нагрузка на отдельные модули) — выделяем модули в сервисы один за другим без переписывания.

## 1.2. Нужен ли Docker — да, обязательно

- Одинаковое окружение у двух разработчиков и на сервере: PostgreSQL 16, Redis, одно приложение.
- Деплой на VPS одной командой.
- Откат — просто запуск предыдущего образа.
- CI запускает тесты в тех же контейнерах, что и прод.

Используем `docker-compose.yml` для разработки и `docker-compose.prod.yml` для сервера.

## 1.3. Нужен ли Postman — да, но лучше Bruno

- **Bruno** (рекомендую) — бесплатный, open-source, коллекции хранятся файлами в Git (`bruno/` в репозитории). Ревью изменений API-коллекций в PR.
- **Postman** — бесплатный тариф есть, но коллекции в облаке, в Git не положишь.

Заводим коллекцию Bruno с первой сессии и дополняем её в каждой сессии, где появляются эндпоинты.

## 1.4. Онлайн-чат — бесплатный вариант

- **Crisp** (free plan) — виджет на сайт, до 2 операторов, история диалогов. Самый простой путь.
- **Chatwoot** (open-source, self-hosted) — полностью бесплатно, но нужно держать на своём сервере.
- Рекомендация: старт — Crisp free; если упираемся в лимиты — Chatwoot.

В архитектуре оставляем модуль `chat/` — заглушка-интеграция: сейчас вставляем скрипт Crisp, позже можем заменить своим WebSocket-чатом.

## 1.5. Стек (финально)

| Слой | Технология |
|---|---|
| Frontend | React 18 + TypeScript + Vite + React Router 6 + TanStack Query + Zustand + React Hook Form + Zod + SCSS Modules |
| Backend | Java 21 + Spring Boot 3 + Spring Security 6 + Spring Data JPA + Flyway + MapStruct |
| БД | PostgreSQL 16 |
| Кэш/сессии | Redis 7 |
| PDF | OpenPDF / Flying Saucer |
| Excel | Apache POI (выгрузка/загрузка .xlsx для 1С) |
| Тесты | JUnit 5 + Testcontainers + Playwright + Vitest |
| Инфра | Docker + Compose, Nginx, GitHub Actions |
| Мониторинг | Actuator + Prometheus + Grafana (позже) |
| Чат | Crisp free (виджет) |
| API-коллекции | Bruno |

## 1.6. Зафиксированные решения (открытых архитектурных вопросов нет)

| Вопрос | Решение |
|---|---|
| Публичный сайт | Розничный каталог **с ценами** и заказом для физлиц (как на сайт-образце). Гостевого checkout нет: для оформления физлицо проходит короткую регистрацию (email/телефон + пароль, роль CUSTOMER_INDIVIDUAL). Онлайн-оплата (ЕРИП) — post-MVP; в MVP розница оплачивается при получении/самовывозе. |
| Нумерация документов | Только PostgreSQL-sequences на комбинацию (тип, серия, год); `max(number)+1` запрещён (см. 2.2). |
| Конкурентный резерв остатков | `SELECT ... FOR UPDATE` по строке остатка (product_id, location_id) в транзакции заказа — параллельный заказ ждёт и видит уже уменьшенный остаток. |
| Доступ к файлам | Приватные — стриминг через backend с проверкой прав; публичные (фото товаров) — `visibility='PUBLIC'` + непредсказуемый `storage_key` (UUID). |
| Идемпотентность | `POST /orders`, `POST /site/requests` принимают заголовок `Idempotency-Key` (хранится 24 ч, повтор возвращает тот же результат). |
| Валюта/локаль/время | BYN, ru-RU; БД хранит UTC, отображение — Europe/Minsk. |
| Soft delete | Только `products` и `users` (`deleted_at`); остальное — статусы/архив. |
| Каталог ошибок API | Единый список кодов `error.code` (AUTH_*, STOCK_*, VALIDATION_*...) в `shared/openapi.yaml` с S07. |
| Cookies | Refresh-токен: httpOnly + Secure + SameSite=Strict. Другие cookies по мере появления — тоже SameSite=Strict. |

## 1.7. Бренд и визуальные образцы (зафиксировано)

**Источник дизайн-токенов — бренд-бук** (6 страниц JPEG, копируется в репозиторий: `docs/brandbook/`). Палитра сверена со страницей «Фирменные цвета»:

| Цвет | HEX | Назначение |
|---|---|---|
| Бордо | `#7C2D24` | Основной: знак, кнопки, шапка, акцентные блоки |
| Сливки | `#F5EDDE` | Фон страниц, карточки, этикетки |
| Желток | `#E5A33C` | Акцент: ручка клоша, «в корзину», ценники, акции |
| Графит | `#26201C` | Текст, монохром |
| Крафт (доп.) | `#C9A875` | Допустимый фон носителя (упаковка), не для UI |

Пропорции: 60% сливки / 25% бордо / 10% графит / 5% желток.
Запреты: бордо на графите (нет контраста); желток не как фон больших блоков; без новых цветов; без теней, обводок, градиентов; знак не растягивать/не поворачивать.

Типографика (подтверждена Google Fonts на сайт-образце):

| Элемент | Шрифт |
|---|---|
| H1, логотип, цены | **Russo One** |
| H2, кнопки, меню | **Oswald** (прописные, разрядка 4–8 px) |
| Основной текст | **Rubik** (межстрочный 1.5–1.6) |

**Сайт-образец публичной части:** https://tkxvh547skm54.kimi.pro — визуальный эталон лендинга и каталога с розничными ценами. Структура главной: шапка (Каталог, Доставка, О нас, Кабинет), hero «Свежая поставка каждое утро», «Хиты недели» (карточки с фото, ценой «р./кг», бейджами ХИТ/НОВИНКА), блок «Как мы доставляем» (3 шага), футер. Кабинеты и CRM — тот же бренд-стиль, но табличный «деловой» формат.

---

# ЧАСТЬ 2. БИЗНЕС-ПРАВИЛА

## 2.1. Роли и права (финальная матрица)

| Функция | Руководитель (ADMIN) | Бухгалтер | Менеджер | Клиент-юрлицо | Клиент-физлицо |
|---|---|---|---|---|---|
| Каталог на сайте с розничными ценами | + | + | + | + | + |
| Прайс/скидки в кабинете | + | − | + | + (свои) | − |
| Оформление заказа | + | − | + (от имени клиента) | + | + (розница) |
| Свои заказы | + | − | + (назначенные клиенты) | + | + |
| Выгрузка счёта/ТТН | + | + | + | + (свои) | − |
| Создание счёта/ТН/ТТН | + | + | + | − | − |
| Загрузка файлов из 1С | + | + | − | − | − |
| Выгрузка файлов для 1С | + | + | + | − | − |
| CRUD клиентов | + | − | − | − | − |
| CRUD товаров, цен, скидок | + | − | − | − | − |
| Склад: просмотр остатков | + | + | + | − | − |
| Склад: приход/расход/списание | + | + | + | − | − |
| Склад: поставщики, заказы поставщикам | + | + | + | − | − |
| Склад: перемещение, инвентаризация | + | + | − | − | − |
| CRM: лиды, задачи, воронка | + | − | + | − | − |
| Дашборды и статистика | + | + | + (свои клиенты) | − | − |
| Аудит | + | − | − | − | − |
| 2FA | обязательно | обязательно | опционально | − | − |

## 2.2. Нумерация документов

| Документ | Формат | Пример |
|---|---|---|
| Заказ | `З-<год>-<порядковый>` | `З-2026-00042` |
| Счёт | `СЧ-<порядковый> от <дата>` | `СЧ-1 от 24.08.2026` |
| ТТН | `ТТН-<серия>-<порядковый> от <дата>` | `ТТН-А-1 от 12.08.2026` |
| ТН | `ТН-<серия>-<порядковый> от <дата>` | `ТН-А-1 от 12.08.2026` |
| Складской документ | `<ТИП>-<порядковый>` | `ПН-17`, `РС-4` |

Серия задаётся в настройках. Порядковый номер сквозной в рамках серии/года.

**Механизм (обязателен):** номер выдаётся только PostgreSQL-sequence на комбинацию (тип документа, серия, год): `doc_seq_<type>_<series>_<year>`, для заказов — `order_seq_<year>`. Sequence создаётся миграцией/задачей при смене года или серии. Выборка `max(number)+1` запрещена — гонки и дубли под параллельной нагрузкой.

## 2.3. НДС

- По умолчанию 10% (как в примере ТТН), настраивается на уровне товара и системы.

## 2.4. Реквизиты (из примера ТТН)

Грузоотправитель (продавец по умолчанию в демо-данных):
`ЧТУП «ЛорСан», УНП 191536521, 220028 г. Минск, ул. Бородинская, д. 1Б, пом. 14, РБ`

Пример покупателя из ТТН: `ООО «ТиоптТрейд», УНП 192953686, г. Минск, ул. Монтажников, 39-109Б, РБ`.

> Это демо-значения. В проде реквизиты ГУСТО задаются в настройках.

## 2.5. Ценообразование

Приоритет цены для клиента:
1. Персональная цена (`customer_prices`).
2. Скидка клиента по бренду/категории (`customer_discounts`).
3. Базовая цена прайс-листа (`product_prices`).

Цена в заказе фиксируется снапшотом и не меняется при изменении прайса.

## 2.6. Уведомления

| Событие | Менеджер | Клиент |
|---|---|---|
| Новый заказ | Telegram + Email | Email (если нет Telegram) / Telegram |
| Смена статуса | Telegram | Email / Telegram |
| Новая заявка с сайта | Telegram + Email | — |
| Счёт выставлен | — | Email / Telegram |

---

# ЧАСТЬ 3. СХЕМА БД (PostgreSQL)

## 3.1. Таблицы

```sql
-- V1 начинается с расширений:
-- CREATE EXTENSION IF NOT EXISTS citext;  (email CITEXT)
-- CREATE EXTENSION IF NOT EXISTS pg_trgm; (поиск по названию товара)

-- Пользователи и роли
users (id UUID PK, email CITEXT UNIQUE, password_hash TEXT, full_name TEXT,
       phone TEXT, role VARCHAR(20) CHECK (role IN ('ADMIN','ACCOUNTANT','MANAGER','CUSTOMER_LEGAL','CUSTOMER_INDIVIDUAL')),
       company_id UUID NULL REFERENCES companies(id),
       totp_secret TEXT NULL, is_active BOOLEAN, created_at, updated_at, deleted_at NULL)

refresh_tokens (id UUID PK, user_id UUID FK, token_hash TEXT UNIQUE,
                user_agent TEXT, ip TEXT, expires_at TIMESTAMPTZ, revoked BOOLEAN)

-- Компании (юрлица)
companies (id UUID PK, name TEXT, short_name TEXT, unp TEXT UNIQUE,
           legal_address TEXT, actual_address TEXT, bank_account TEXT, bank_name TEXT, bank_bic TEXT,
           contact_phone TEXT, contact_email TEXT, status VARCHAR(20),
           manager_id UUID NULL REFERENCES users(id), created_at, updated_at)

-- Каталог
categories (id UUID PK, parent_id UUID NULL, name TEXT, slug TEXT UNIQUE, sort INT, is_active BOOLEAN)
products (id UUID PK, sku TEXT UNIQUE, name TEXT, category_id UUID FK, description TEXT,
          unit TEXT DEFAULT 'кг', manufacturer TEXT, country TEXT DEFAULT 'РБ',
          tnved_code TEXT NULL, barcode TEXT NULL, vat_rate NUMERIC(4,2) DEFAULT 10,
          weight_per_unit NUMERIC(10,3) NULL, is_active BOOLEAN, deleted_at NULL)
product_images (id UUID PK, product_id UUID FK, file_id UUID FK, sort INT)
brands (id UUID PK, name TEXT UNIQUE, slug TEXT UNIQUE)

-- Цены
price_lists (id UUID PK, name TEXT, valid_from DATE, valid_to DATE NULL, is_active BOOLEAN)
product_prices (id UUID PK, price_list_id UUID FK, product_id UUID FK, price NUMERIC(12,2),
                valid_from DATE, valid_to DATE NULL,
                UNIQUE (price_list_id, product_id, valid_from))
customer_prices (id UUID PK, company_id UUID FK, product_id UUID FK, price NUMERIC(12,2),
                 valid_from DATE, valid_to DATE NULL,
                 UNIQUE (company_id, product_id, valid_from))
customer_discounts (id UUID PK, company_id UUID FK, brand_id UUID FK NULL, category_id UUID FK NULL,
                    discount_percent NUMERIC(5,2), valid_from DATE, valid_to DATE NULL)

-- Склад (полноценный учёт уровня «МойСклад»)
stock_locations (id UUID PK, name TEXT, address TEXT, is_active BOOLEAN)
suppliers (id UUID PK, name TEXT, unp TEXT NULL, phone TEXT, email TEXT,
           contact_person TEXT, note TEXT, is_active BOOLEAN, created_at)

-- Заказ поставщику
purchase_orders (id UUID PK, number TEXT UNIQUE, supplier_id UUID FK, status VARCHAR(20)
                 CHECK (status IN ('DRAFT','SENT','PARTIAL','RECEIVED','CANCELLED')),
                 expected_date DATE NULL, total_amount NUMERIC(12,2), note TEXT,
                 created_by UUID FK, created_at, updated_at)
purchase_order_items (id UUID PK, purchase_order_id UUID FK, product_id UUID FK,
                      quantity NUMERIC(12,3), purchase_price NUMERIC(12,2),
                      received_quantity NUMERIC(12,3) DEFAULT 0)

-- Складские документы: приход / расход / перемещение / списание / инвентаризация
warehouse_documents (id UUID PK, number TEXT UNIQUE, type VARCHAR(15)
                     CHECK (type IN ('INCOMING','OUTGOING','TRANSFER','WRITE_OFF','INVENTORY')),
                     status VARCHAR(20) CHECK (status IN ('DRAFT','CONFIRMED','CANCELLED')),
                     location_from_id UUID FK NULL, location_to_id UUID FK NULL,
                     supplier_id UUID FK NULL, purchase_order_id UUID FK NULL,
                     customer_order_id UUID FK NULL,   -- для расхода по заказу клиента
                     document_date DATE, note TEXT,
                     created_by UUID FK, created_at, confirmed_at NULL)
warehouse_document_items (id UUID PK, document_id UUID FK, product_id UUID FK,
                          quantity NUMERIC(12,3), price NUMERIC(12,2) NULL)

-- Движения создаются ТОЛЬКО при CONFIRMED складского документа или заказа
stock_movements (id UUID PK, product_id UUID FK, location_id UUID FK,
                 type VARCHAR(10) CHECK (type IN ('INCOMING','OUTGOING','ADJUSTMENT','RESERVE','RELEASE')),
                 quantity NUMERIC(12,3), reference_type TEXT NULL, reference_id UUID NULL,
                 note TEXT, created_by UUID FK, created_at)
-- Остаток = SUM(quantity) по (product_id, location_id); доступно = остаток − резерв
-- Индексы: (product_id, location_id), (created_at)

-- Отчёты склада (SQL-вьюхи, не таблицы):
--   v_stock_balance   — остатки по складам/товарам
--   v_stock_available — доступно с учётом резерва
--   v_stock_turnover  — оборачиваемость за период
--   v_stock_to_order  — товары ниже минимального остатка
products ... + min_stock NUMERIC(12,3) DEFAULT 0  -- минимальный остаток для уведомлений

-- Заказы
orders (id UUID PK, number TEXT UNIQUE, customer_company_id UUID FK NULL,
        customer_user_id UUID FK, manager_id UUID FK NULL,
        status VARCHAR(20) CHECK (status IN ('NEW','CONFIRMED','PROCESSING','READY','SHIPPED','COMPLETED','CANCELLED')),
        delivery_type VARCHAR(20) CHECK (delivery_type IN ('PICKUP','DELIVERY')),
        delivery_address TEXT NULL, note TEXT,
        total_amount NUMERIC(12,2), total_vat NUMERIC(12,2), created_at, updated_at)
order_items (id UUID PK, order_id UUID FK, product_id UUID FK,
             product_snapshot JSONB, quantity NUMERIC(12,3), unit_price NUMERIC(12,2),
             vat_rate NUMERIC(4,2), total NUMERIC(12,2), note TEXT NULL)

-- Корзина (persist в БД для авторизованных)
carts (id UUID PK, user_id UUID FK UNIQUE, created_at, updated_at)
cart_items (id UUID PK, cart_id UUID FK, product_id UUID FK, quantity NUMERIC(12,3),
            UNIQUE (cart_id, product_id))

-- Outbox: надёжная доставка уведомлений (email/Telegram)
outbox_messages (id UUID PK, aggregate_type TEXT, aggregate_id UUID, type TEXT, payload JSONB,
                 status VARCHAR(10) CHECK (status IN ('PENDING','SENT','FAILED')),
                 attempts INT DEFAULT 0, next_attempt_at TIMESTAMPTZ, sent_at TIMESTAMPTZ NULL, created_at)
-- Индекс: (status, next_attempt_at) для поллера отправки

-- Подписки уведомлений (привязка Telegram chat_id к пользователю)
notification_subscriptions (id UUID PK, user_id UUID FK,
                            channel VARCHAR(10) CHECK (channel IN ('TELEGRAM','EMAIL')),
                            destination TEXT, is_active BOOLEAN, created_at,
                            UNIQUE (user_id, channel, destination))

-- Документы
invoices (id UUID PK, number TEXT, series TEXT, issue_date DATE, order_id UUID FK,
          seller_snapshot JSONB, buyer_snapshot JSONB,
          total_amount NUMERIC(12,2), total_vat NUMERIC(12,2),
          status VARCHAR(20) CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','CANCELLED')),
          pdf_file_id UUID FK NULL, created_by UUID FK, created_at)
invoice_items (id UUID PK, invoice_id UUID FK, product_snapshot JSONB,
               quantity NUMERIC(12,3), unit_price NUMERIC(12,2), vat_rate NUMERIC(4,2), total NUMERIC(12,2))

waybills (id UUID PK, type VARCHAR(5) CHECK (type IN ('TN','TTN')), number TEXT, series TEXT,
          issue_date DATE, invoice_id UUID FK NULL, order_id UUID FK,
          seller_snapshot JSONB, buyer_snapshot JSONB, carrier_snapshot JSONB NULL,
          pdf_file_id UUID FK NULL, created_by UUID FK, created_at)
waybill_items (id UUID PK, waybill_id UUID FK, product_snapshot JSONB,
               quantity NUMERIC(12,3), unit_price NUMERIC(12,2), vat_rate NUMERIC(4,2),
               total NUMERIC(12,2), weight NUMERIC(12,3) NULL)

payments (id UUID PK, invoice_id UUID FK, amount NUMERIC(12,2), paid_at DATE,
          method TEXT, note TEXT, created_by UUID FK, created_at)

-- CRM
leads (id UUID PK, source TEXT, name TEXT, phone TEXT, email TEXT, company_name TEXT NULL,
       message TEXT, status VARCHAR(20), assigned_manager_id UUID NULL, created_at, updated_at)
crm_tasks (id UUID PK, assignee_id UUID FK, company_id UUID FK NULL, title TEXT, description TEXT,
           due_date TIMESTAMPTZ, status VARCHAR(20), created_at)
crm_notes (id UUID PK, company_id UUID FK, author_id UUID FK, body TEXT, created_at)

-- Заявки с сайта
site_requests (id UUID PK, name TEXT, phone TEXT, email TEXT, message TEXT,
               type VARCHAR(20), status VARCHAR(20), created_at)

-- CMS
articles (id UUID PK, slug TEXT UNIQUE, title TEXT, body TEXT, status VARCHAR(20),
          published_at TIMESTAMPTZ NULL, created_by UUID FK, created_at, updated_at)

-- Файлы
files (id UUID PK, storage_key TEXT UNIQUE, original_name TEXT, mime_type TEXT,
       size_bytes BIGINT, checksum TEXT, owner_id UUID FK NULL, visibility VARCHAR(20),
       created_at)

-- Аудит
audit_log (id UUID PK, actor_id UUID FK, action TEXT, target_type TEXT, target_id UUID,
           before JSONB, after JSONB, created_at)

-- Обмен с 1С (файлы)
integration_files (id UUID PK, direction VARCHAR(10) CHECK (direction IN ('IMPORT','EXPORT')),
                   type VARCHAR(30) CHECK (type IN ('PRICES','STOCK','ORDERS','INVOICES','WAYBILLS')),
                   file_id UUID FK, status VARCHAR(20), rows_total INT, rows_ok INT, rows_error INT,
                   error_log_file_id UUID FK NULL, processed_by UUID FK NULL, created_at, processed_at NULL)

-- Настройки
settings (key TEXT PK, value JSONB)
-- 'document.series.ttn', 'document.series.tn', 'seller.requisites', 'vat.default'
```

## 3.2. Снапшоты (важно)

`product_snapshot`, `seller_snapshot`, `buyer_snapshot` — JSONB с копией данных на момент документа. После выпуска документа данные не меняются никогда.

```json
// product_snapshot
{ "sku": "A-100", "name": "Филе куриное", "unit": "кг", "tnved": "0207143009" }
// seller_snapshot
{ "name": "ЧТУП «ЛорСан»", "unp": "191536521", "address": "...", "bank_account": "...", "bank_name": "...", "bank_bic": "..." }
```

---

# ЧАСТЬ 4. API (каркас OpenAPI)

Префикс `/api/v1`. Envelope: `{data, meta, error}`.

## 4.1. Группы эндпоинтов

- `POST /auth/login|register|refresh|logout`, `GET /auth/me`, `POST /auth/2fa/enable|verify`, `POST /auth/password-reset/request|confirm`
- `GET /catalog/categories|products|products/{sku}` — публично, розничные цены
- `GET /cabinet/catalog` — кабинет юрлица: компактный список с ценами клиента
- `GET|DELETE /cart`, `PUT /cart/items/{productId}` — корзина авторизованного (юрист: quantity, 0 = удалить)
- `GET|POST /orders`, `GET /orders/{id}`, `PATCH /orders/{id}/status` — создание идемпотентно по `Idempotency-Key`
- `GET|POST /invoices`, `GET /invoices/{id}/pdf`
- `GET|POST /waybills`, `GET /waybills/{id}/pdf`
- `GET|POST /payments`
- `GET|POST /manager/clients`, `PUT /manager/clients/{id}/discounts`
- `GET /warehouse/balance|documents|suppliers`, `POST /warehouse/documents|purchase-orders`
- `GET /crm/leads|tasks`, `POST /crm/leads|tasks|notes`
- `GET /accountant/...` — документы, платежи, отчёты
- `GET /admin/users|audit|settings`, `POST /admin/import/prices|stock` (xlsx)
- `GET /integration/export/orders|invoices` (xlsx)
- `POST /site/requests` — заявки с сайта
- `GET /reports/dashboard` — статистика
- `GET /healthz`, `GET /readyz`

Полный `shared/openapi.yaml` заполняется по мере сессий (каждая сессия, добавляющая эндпоинт, дополняет файл).

---

# ЧАСТЬ 5. ДЕТАЛЬНЫЙ ПЛАН СЕССИЙ

> Формат сессии: цель, задачи, приёмка, git-команды.
> Обозначения: **[A]** — Dev A (frontend), **[B]** — Dev B (backend), **[AB]** — вместе.
> Стандартный git-блок в конце каждой сессии сокращён до `GIT(STD)` и раскрыт в Части 6.

## Неделя 1 — Фундамент

### S01 [AB] Репозиторий и CI
> ✅ Выполнено 2026-08-24 [AB] — репозиторий на GitHub (main/develop), CI: gitleaks + backend + frontend, бренд-бук в docs/brandbook.

- GitHub-репозиторий `gusto-b2b`, ветки `main`/`develop`, защита веток (PR, CI зелёный).
- `README.md`, `.gitignore`, `LICENSE` (private), Conventional Commits.
- GitHub Actions: backend build+test, frontend lint+build, validate openapi, secret-scanning (gitleaks).
- **Приёмка:** пустой коммит проходит CI.
- Git: `git checkout -b chore/init && git commit -m "chore: init repository" && git push -u origin chore/init` → PR → merge.

### S02 [B] Backend-скелет
> ✅ Выполнено 2026-08-24 [AB] — Spring Boot 3.5 / Java 21, Web+Validation+JPA+Security+Actuator+Flyway, envelope, /healthz и /readyz, Dockerfile. ESLint-аналог для Java и MapStruct/Lombok — в первых backend-сессиях.

- Spring Boot 3 + Maven: Web, Validation, JPA, PostgreSQL, Flyway, Security, Actuator, Lombok, MapStruct.
- Пакеты по модулям (см. Часть 1). Глобальный exception handler, envelope ответов.
- `/healthz`, `/readyz` через Actuator.
- **Приёмка:** `mvn test` зелёный, health OK.
- `GIT(STD)` — `feat(backend): spring boot skeleton`.

### S03 [A] Frontend-скелет
> ✅ Выполнено 2026-08-24 [AB] — Vite + React 18 + TS strict, SCSS-токены бренд-бука (_tokens.scss), бренд-шрифты, прокси /api → backend, Dockerfile, package-lock.json. ESLint — добавить первым frontend-коммитом.

- Vite + React 18 + TS strict, ESLint, Prettier.
- SCSS-переменные из бренд-бука (`_tokens.scss`, токены из Части 1.7): цвета, шрифты (Russo One, Oswald, Rubik через Google Fonts).
- Бренд-бук скопирован в `docs/brandbook/` (6 JPEG).
- React Router, базовые layouts (public/cabinet), страница 404.
- **Приёмка:** `npm run build` OK, dev-сервер поднимается.
- `GIT(STD)` — `feat(frontend): vite react skeleton`.

### S04 [AB] Docker окружение
> ✅ Выполнено 2026-08-24 [AB] — docker-compose (postgres:16, redis:7, backend, frontend, healthchecks, volume pgdata), .env.example, Makefile (up/infra/down/clean/logs/psql). Стек поднят и проверен: healthz UP, frontend 200, прокси работает.

- `docker-compose.yml`: postgres:16, redis:7, backend, frontend (dev-режим).
- `.env.example` со всеми переменными.
- Makefile: `make up`, `make infra`, `make down`, `make clean`, `make logs`, `make psql`.
- **Приёмка:** `make up` поднимает всё, frontend видит backend.
- `GIT(STD)` — `feat(infra): docker compose dev environment`.

### S05 [B] БД V1: baseline-миграция
> ✅ Выполнено 2026-08-25 [B] — V1__baseline: 38 таблиц по Части 3 (FK, индексы, CHECK; citext/pg_trgm); V2__seed: админ (bcrypt из .env через плейсхолдеры Flyway), настройки серий/НДС/реквизитов. flyway-maven-plugin (`mvn flyway:migrate`), Testcontainers-тест схемы. Вьюхи склада — S18, pg_trgm-индекс по name — S13, sequence-нумерация — S20.
- Flyway `V1__baseline.sql` по схеме из Части 3 (все таблицы, индексы, CHECK-ограничения).
- Seed: роли, админ-пользователь (пароль в `.env`, bcrypt), настройки документов.
- **Приёмка:** `mvn flyway:migrate` на чистой БД, интеграционный тест схемы.
- `GIT(STD)` — `feat(db): baseline migration V1`.

### S06 [A] Дизайн-система и UI-kit
> ✅ Выполнено 2026-08-24 [A] — 11 компонентов (Button, Input, Textarea, Select, Table, Card, Badge, Modal, Toast+useToast, Pagination, Tabs), страница /ui-kit со всеми состояниями, роутер + 404. ESLint+Prettier первым коммитом; lint включён в CI; hot-reload volume для frontend. Приёмка: скриншот /ui-kit сверен с бренд-буком (цвета/шрифты/запреты соблюдены).

- Компоненты: Button, Input, Select, Table, Card, Badge, Modal, Toast, Pagination, Tabs.
- Строго по бренд-буку: без теней/градиентов, пропорции цветов.
- Страница `/ui-kit` со всеми состояниями.
- **Приёмка:** скриншоты сверены с бренд-буком.
- `GIT(STD)` — `feat(frontend): design system ui-kit`.

### S07 [AB] Bruno-коллекция + OpenAPI
> ✅ Выполнено 2026-08-25 [A] — `shared/openapi.yaml` baseline: envelope, auth-группа (login/register/refresh/logout/me/2fa/password-reset), каталог кодов ошибок (`AUTH_*`, `STOCK_*`, `VALIDATION_*` + общие), пагинация `meta:{page,size,total}`, `Idempotency-Key`; Bruno-коллекция `health/` + `auth/` с переменными `host/email/password`; `npm run generate-api` (openapi-typescript) + `src/api/schema.d.ts` в `.gitignore` + шаг генерации в CI; проверено: build зелёный, health-запросы против живого backend OK.
- `shared/openapi.yaml`: envelope, auth-эндпоинты.
- Каталог кодов ошибок (`error.code`: AUTH_*, STOCK_*, VALIDATION_*...) и формат пагинации (`meta: page/size/total`) — фиксируются здесь и дальше только расширяются.
- `bruno/` коллекция: auth login/refresh/me.
- Скрипт `npm run generate-api` (openapi-typescript).
- **Приёмка:** коллекция работает против живого backend.
- `GIT(STD)` — `feat(contracts): openapi baseline and bruno collection`.

## Неделя 2 — Auth и пользователи

### S08 [B] JWT + refresh
> ✅ Выполнено 2026-08-31 [B] — JWT access 15 мин + refresh (httpOnly Secure SameSite=Strict, 7 дней, rotation + reuse detection), таблица refresh_tokens, revoke на logout, саморегистрация физлиц, восстановление пароля по хэшированному токену, Redis rate limiting 5/15 мин, интеграционные тесты, Bruno-коллекция auth.
- Login (email+password) → access 15 мин + refresh (httpOnly cookie, Secure, SameSite=Strict, 7 дней, rotation + reuse detection: повтор старого refresh ревокает всю цепочку).
- Таблица `refresh_tokens`, revoke на logout.
- Саморегистрация физлица `POST /auth/register` (email/телефон + пароль, роль CUSTOMER_INDIVIDUAL, без 2FA).
- Восстановление пароля: `POST /auth/password-reset/request|confirm` (одноразовый токен на 30 мин, хранится хэшем).
- Rate limiting с первого дня: `/auth/login`, `/auth/register`, `/auth/password-reset/*` — 5 попыток / 15 мин на IP+email (Redis).
- Тесты: login/refresh/logout/expired/lockout.
- **Приёмка:** Bruno-коллекция auth зелёная.
- `GIT(STD)` — `feat(auth): jwt access and refresh flow`.

### S09 [B] Роли, guards, ownership
- `@PreAuthorize`/method security по ролям.
- Ownership-aspect: менеджер видит только назначенные `companies`, клиент — только свою компанию.
- 2FA TOTP для ADMIN/ACCOUNTANT (setup, verify, recovery codes).
- **Приёмка:** негативные тесты 401/403, IDOR-тесты.
- `GIT(STD)` — `feat(auth): rbac ownership and 2fa`.

### S10 [A] Страницы входа
> ✅ Выполнено 2026-08-25 [A] — страницы login/2FA, запрос/сброс пароля; `AuthInit` (silent refresh по куке), `ProtectedRoute`, редиректы по ролям (admin→/admin, manager→/manager, клиент→/cabinet); API-клиент, Zustand-стор, валидация react-hook-form + zod; UI в бренд-стиле через UI-kit. Добавлены deps: zustand, @tanstack/react-query, react-hook-form, zod, @hookform/resolvers. lint+build зелёные; полный цикл входа вручную — после реализации S08 бэкендером.
- Login, восстановление пароля, 2FA-ввод.
- Защищённые роуты, редирект по роли (admin→/admin, manager→/manager, customer→/cabinet).
- **Приёмка:** полный цикл входа вручную.
- `GIT(STD)` — `feat(frontend): auth pages and routing`.

### S11 [B] CRUD пользователей и компаний
- Админ создаёт бухгалтера/менеджера/клиента. Привязка `users.company_id`.
- Компании: УНП уникален, привязка `manager_id`, контакты.
- Сброс пароля админом (временный пароль, показать 1 раз).
- **Приёмка:** интеграционные тесты CRUD + валидация УНП.
- `GIT(STD)` — `feat(users): companies and users crud`.

### S12 [A] Админка: пользователи и компании
- Таблицы с фильтрами/поиском, формы создания, назначение менеджера.
- **Приёмка:** админ создаёт клиента через UI.
- `GIT(STD)` — `feat(frontend): admin users and companies`.

## Неделя 3 — Каталог

### S13 [B] Каталог API
- CRUD категорий (дерево), товаров, брендов.
- Публичные `GET /catalog/*` с пагинацией/поиском/фильтрами, розничные цены из активного прайса.
- Индексы: `pg_trgm` на `products.name`.
- **Приёмка:** Bruno-коллекция catalog; поиск по части слова.
- `GIT(STD)` — `feat(catalog): api with search and filters`.

### S14 [A] Публичный каталог
- Главная (по лендингу-образцу: hero, категории, хиты, «как мы доставляем», футер).
- Каталог: сетка карточек с розничными ценами, фильтры, поиск, карточка товара.
- **Приёмка:** визуальное соответствие лендингу и бренд-буку.
- `GIT(STD)` — `feat(frontend): public catalog pages`.

### S15 [B] Прайс-листы и персональные цены
- `price_lists`, `product_prices`, `customer_prices`, `customer_discounts`.
- `PricingService`: приоритет персональная → скидка → базовая; период действия.
- Эндпоинт кабинета `GET /cabinet/catalog` с ценой клиента.
- Тесты приоритета и периодов.
- **Приёмка:** юнит-тесты PricingService, кабинетная цена отличается от розницы.
- `GIT(STD)` — `feat(pricing): price lists and customer prices`.

### S16 [A] Кабинет юрлица: компактный каталог
- Таблица: артикул, название, ед.изм., цена клиента, наличие, поле количества, «в корзину».
- Быстрый поиск, фильтр по категории/бренду, массовое добавление (вставка списка SKU).
- **Приёмка:** клиент добавляет 20 позиций за минуту.
- `GIT(STD)` — `feat(frontend): cabinet compact catalog`.

### S17 [B] Фото товаров и файлы
- Модуль `files`: загрузка (magic-bytes, лимиты), хранение в локальном volume (S3-подобный API, совместимость на будущее).
- Модель доступа (см. 1.6): приватные файлы — стриминг через backend с проверкой прав; публичные (фото товаров) — `visibility='PUBLIC'`, `storage_key` = непредсказуемый UUID.
- `product_images`, выдача по защищённым URL.
- **Приёмка:** загрузка фото товара из админки, показ в каталоге.
- `GIT(STD)` — `feat(files): storage module and product images`.

## Неделя 4 — Склад и заказы

### S18 [B] Склад: движения, остатки, резерв
- `stock_movements`, расчёт остатка (`v_stock_balance`), доступно с резервом (`v_stock_available`).
- Статус для клиента: `IN_STOCK`/`PREORDER` из доступного остатка.
- Резерв выполняется в транзакции заказа через `SELECT ... FOR UPDATE` строки остатка (product_id, location_id) — механизм из 1.6.
- Тест на параллельный резерв (oversell guard).
- **Приёмка:** интеграционные тесты остатков.
- `GIT(STD)` — `feat(inventory): stock movements and reservation`.

### S18.1 [B] Складские документы: приход, расход, списание
- `warehouse_documents` + `warehouse_document_items` (DRAFT → CONFIRMED → движения).
- Приход (от поставщика / без заказа), расход (по заказу клиента), списание (порча/бой).
- Подтверждение документа — единственная точка создания движений.
- **Приёмка:** тесты: приход +100, расход −30, остаток 70; отмена CONFIRMED запрещена без сторно.
- `GIT(STD)` — `feat(inventory): warehouse documents incoming outgoing writeoff`.

### S18.2 [B] Поставщики и заказы поставщикам
- `suppliers` CRUD, `purchase_orders` + items, статусы DRAFT→SENT→PARTIAL→RECEIVED.
- Частичный приём: приходной документ ссылается на заказ поставщику, `received_quantity` накапливается.
- **Приёмка:** цикл «заказал 100 → принял 60 → принял 40 → RECEIVED».
- `GIT(STD)` — `feat(inventory): suppliers and purchase orders`.

### S18.3 [B] Перемещение, инвентаризация, отчёты склада
- Перемещение между складами (TRANSFER), инвентаризация (INVENTORY: факт vs учётный → ADJUSTMENT).
- `min_stock` на товаре + отчёт `v_stock_to_order` (что заказать у поставщиков).
- Отчёты: остатки по складам, оборачиваемость, движения за период.
- **Приёмка:** тесты отчётов на фикстуре 3 складов × 20 SKU.
- `GIT(STD)` — `feat(inventory): transfer inventory and stock reports`.

### S18.4 [A] Склад UI (CRM)
- Раздел «Склад»: остатки (таблица с фильтрами по складу/категории), документы (создание прихода/расхода/списания), поставщики, заказы поставщикам, отчёты.
- Подсветка товаров ниже `min_stock` (желток-акцент по бренд-буку).
- **Приёмка:** менеджер/бухгалтер проводит полный складской цикл через UI.
- `GIT(STD)` — `feat(frontend): warehouse ui`.

### S19 [A] Статусы наличия в UI
- Бейджи «В наличии»/«Под заказ» в каталоге и кабинете.
- `GIT(STD)` — `feat(frontend): stock badges`.

### S20 [B] Заказы: создание
- Корзина `carts`/`cart_items` (persist в БД для авторизованных), `POST /orders` идемпотентен по заголовку `Idempotency-Key` (24 ч) — двойной клик не создаёт второй заказ.
- Транзакция: проверка остатков → резерв → снапшот цен → создание заказа с номером (sequence, см. 2.2).
- Событие `OrderCreated` → `outbox_messages`.
- **Приёмка:** тест полного цикла; цены не меняются при смене прайса после заказа.
- `GIT(STD)` — `feat(orders): creation with price snapshot`.

### S21 [A] Корзина и оформление
- Корзина в кабинете (таблица, изменение количества, итог с НДС).
- Оформление: доставка/самовывоз, адрес, комментарий, подтверждение.
- Розничная корзина на публичном сайте: оформление требует входа или короткой регистрации физлица (см. 1.6; гостевой checkout — post-MVP).
- **Приёмка:** заказ создаётся из UI.
- `GIT(STD)` — `feat(frontend): cart and checkout`.

### S22 [B] Жизненный цикл заказа
- Смена статусов с правами (менеджер по своим клиентам), отмена с разблокировкой резерва.
- `GET /manager/orders` с фильтрами.
- **Приёмка:** статус-машина покрыта тестами.
- `GIT(STD)` — `feat(orders): status lifecycle`.

### S23 [A] Кабинет: заказы
- История заказов клиента, детали, повтор заказа в 1 клик.
- Менеджер: лента заказов своих клиентов, смена статусов.
- **Приёмка:** цикл NEW→COMPLETED через UI.
- `GIT(STD)` — `feat(frontend): orders cabinet and manager list`.

## Неделя 5 — Документы (счёт, ТН, ТТН)

### S24 [B] Счета: модель и генерация
- `invoices`, `invoice_items`, нумерация `СЧ-N от ДД.ММ.ГГГГ`, статусы.
- Создание из заказа (снапшоты продавца/покупателя/товаров).
- **Приёмка:** тест неизменности снапшота после смены реквизитов.
- `GIT(STD)` — `feat(invoices): model and creation`.

### S25 [B] PDF счёта (бренд)
- Шаблон в фирменном стиле (бордо/сливки/графит, Russo One/Oswald).
- Реквизиты продавца из `settings('seller.requisites')`, покупателя из компании.
- Места под печать/подпись (заглушки).
- Flying Saucer/OpenPDF, сохранение в `files`.
- **Приёмка:** PDF сверен с бренд-буком, открывается и печатается.
- `GIT(STD)` — `feat(invoices): branded pdf generation`.

### S26 [B] ТТН и ТН
- Модели `waybills`, нумерация с серией.
- ТТН по структуре примера (грузоотправитель/получатель/заказчик/авто/водитель/товары/масса/НДС).
- PDF ТН и ТТН.
- **Приёмка:** PDF ТТН визуально соответствует примеру `02.06.2026 ТиоптТрейд.xls`.
- `GIT(STD)` — `feat(waybills): tn and ttn documents with pdf`.

### S27 [A] Документы в UI
- Кабинет клиента: список счетов/накладных, скачивание PDF.
- Менеджер/бухгалтер: выставить счёт из заказа, создать ТТН, список документов с фильтрами.
- **Приёмка:** документы создаются и скачиваются из UI.
- `GIT(STD)` — `feat(frontend): documents lists and download`.

### S28 [B] Платежи и задолженность
- `payments`, статусы счёта: частичная оплата → PARTIALLY_PAID, полная → PAID; остаток долга по компании.
- Отчёт «задолженность по клиентам».
- **Приёмка:** тесты статусов и сводки долга.
- `GIT(STD)` — `feat(payments): payments and debt report`.

## Неделя 6 — CRM, заявки, уведомления

### S29 [B] CRM: лиды, задачи, заметки
- `leads` из заявок сайта, назначение менеджеру, статусы воронки.
- `crm_tasks` (срок, статус), `crm_notes`.
- Дашборд руководителя: выручка, топ-товары, топ-клиенты, долг, конверсия лидов.
- **Приёмка:** интеграционные тесты CRM и отчётов.
- `GIT(STD)` — `feat(crm): leads tasks notes and dashboards api`.

### S30 [A] CRM UI
- Менеджер: мои клиенты, карточка компании (заказы, долг, задачи, заметки), воронка лидов.
- Руководитель: дашборд с графиками (Recharts).
- **Приёмка:** менеджер ведёт клиента от лида до заказа через UI.
- `GIT(STD)` — `feat(frontend): crm pages and dashboard`.

### S31 [B] Заявки с сайта + outbox
- `POST /site/requests`, сохранение → outbox → отправка.
- Повторная отправка при сбое (retry + circuit breaker, Resilience4j).
- **Приёмка:** при выключенном SMTP заявка не теряется.
- `GIT(STD)` — `feat(requests): site requests with outbox`.

### S32 [B] Telegram-бот
- Регистрация бота, webhook за Nginx.
- Подписка менеджера/клиента (привязка по команде/коду).
- Шаблоны уведомлений (новый заказ, статус, счёт).
- **Приёмка:** уведомления приходят в тестовый чат.
- `GIT(STD)` — `feat(notifications): telegram bot`.

### S33 [B] Email
- SMTP (настройки в `.env`), шаблоны писем в бренде (сливки/бордо).
- Правило: клиенту — только если нет Telegram-подписки.
- **Приёмка:** письма уходят на тестовый ящик.
- `GIT(STD)` — `feat(notifications): email templates and sending`.

### S34 [A] Заявка с сайта + чат
- Форма «Стать клиентом»/обратной связи на публичном сайте.
- Виджет Crisp (модуль `chat`, обёртка для замены на свой позже).
- **Приёмка:** заявка создаёт лид в CRM, чат открывается.
- `GIT(STD)` — `feat(frontend): contact form and chat widget`.

## Неделя 7 — Обмен с 1С, CMS, админка

### S35 [B] Импорт прайсов/остатков из 1С (.xlsx)
- `POST /admin/import/prices` и `/stock` (Apache POI).
- Отчёт по строкам (ok/error с номером строки), `integration_files`.
- Правило конфликтов: upsert по SKU; отсутствующие → архив (флаг).
- **Приёмка:** импорт файла 1000 строк < 30 сек, отчёт ошибок.
- `GIT(STD)` — `feat(integration): xlsx import prices and stock`.

### S36 [B] Экспорт для 1С (.xlsx)
- Выгрузка заказов/счетов/накладных за период.
- Колонки согласованы с бухгалтерией (шаблон в `docs/1c-export-format.md`).
- **Приёмка:** файл открывается в Excel, все поля на месте.
- `GIT(STD)` — `feat(integration): xlsx export orders invoices`.

### S37 [A] CMS + страницы
- Админка статей (draft/published), публичные страницы «О нас», «Доставка», «Контакты», «Политика конфиденциальности».
- **Приёмка:** статья публикуется и видна на сайте.
- `GIT(STD)` — `feat(frontend): cms articles and static pages`.

### S38 [AB] Админ-панель: финал
- Настройки (реквизиты продавца, серии документов, НДС, SMTP, Telegram token).
- Журнал аудита с фильтрами.
- **Приёмка:** смена реквизитов отражается в новых PDF.
- `GIT(STD)` — `feat(admin): settings and audit log`.

## Неделя 8 — Качество и продакшен

### S39 [AB] Тестирование полным циклом
- E2E Playwright: регистрация клиента админом → вход → каталог → заказ → счёт → ТТН → оплата.
- Нагрузочное k6: каталог 100 RPS, создание заказа 20 RPS.
- **Приёмка:** E2E зелёный, p95 каталога < 300 мс.
- `GIT(STD)` — `test: e2e and load tests`.

### S40 [AB] Харденинг безопасности
- Rate limiting остальных эндпоинтов (экспорт 10/час; login/register закрыты с S08), security headers, CORS-allowlist.
- `pip-audit`/`mvn versions`/OWASP dependency-check в CI.
- Проверка: нет PII в логах, нет предсказуемых URL файлов.
- **Приёмка:** чек-лист безопасности пройден.
- `GIT(STD)` — `chore(security): hardening checklist`.

### S41 [AB] Продакшен-деплой
- VPS (Ubuntu LTS), `docker-compose.prod.yml`, Nginx + Let's Encrypt, домен `gustomeat.by`.
- Бэкапы: ежедневный pg_dump + WAL → внешнее хранилище, шифрование (age/gpg), restore-test.
- Uptime-мониторинг `/healthz`.
- Runbook в `docs/runbooks.md`.
- **Приёмка:** сайт доступен по HTTPS, бэкап восстанавливается.
- `GIT(STD)` — `feat(infra): production deployment`.

### S42 [AB] Заполнение и запуск
- Демо-данные заменены реальными (товары, прайсы, реквизиты).
- Заведены менеджер, бухгалтер, руководитель (2FA), 3 тестовых клиента.
- **Приёмка:** полный бизнес-цикл на проде.

## Пост-MVP (после запуска)

- Онлайн-оплата розницы (ЕРИП) и гостевой checkout без регистрации.
- Telegram Mini App (кабинет клиента внутри Telegram).
- PWA-мобильное приложение.
- Детальная аналитика и ABC/XYZ-анализ товаров.
- Свой чат вместо Crisp (если нужно).

---

# ЧАСТЬ 6. GIT-ПРАВИЛА ПОСЛЕ КАЖДОЙ СЕССИИ

`GIT(STD)` раскрывается так:

```bash
# 1. Обновиться
git checkout develop && git pull origin develop

# 2. Ветка под сессию
git checkout -b feature/<dev>-s<номер>-<кратко>   # пример: feature/b-s08-jwt

# 3. ...работа, локальные коммиты...

# 4. Перед пушем (те же команды, что в 6.3 и AGENTS.md)
mvn -B verify -f backend/pom.xml                                    # backend: unit + интеграционные
npm --prefix frontend run lint && npm --prefix frontend run build   # frontend
# `npm run test` добавляется сюда, когда в frontend появится Vitest

# 5. Коммит и пуш
git add -A
git commit -m "feat(<module>): <что сделано> [S<номер>]"
git push -u origin feature/<dev>-s<номер>-<кратко>

# 6. Pull Request в develop
#    - описание: что сделано, как проверить, скриншоты
#    - ревью второго разработчика
#    - CI зелёный
#    - merge (squash)

# 7. После merge
git checkout develop && git pull origin develop
git branch -d feature/<dev>-s<номер>-<кратко>
```

Правила:
- **Миграции БД** пишет только Dev B (владелец схемы). Dev A ставит задачу, если нужно поле.
- **`shared/openapi.yaml`** обновляется в той же сессии, где меняется эндпоинт. После merge оба выполняют `npm run generate-api`.
- **Конфликты** — обсуждение в PR, решение фиксируется в `docs/decisions.md`.
- **Запрещено** коммитить в `develop`/`main` напрямую.
- **Ветка по умолчанию на GitHub — `develop`**: новые PR автоматически целются в неё; `main` — стабильная, обновляется мержем из `develop`.

## 6.1. Синхронизация локального окружения (после чужого merge)

Через Git между разработчиками едут **код, схема БД (миграции) и сид-данные — но не сами данные**. База у каждого локальная (volume `pgdata`), её содержимое не синхронизируется и не должно.

После того как коллега смержил свою работу в `develop`:

```bash
git checkout develop && git pull   # новые файлы у тебя на диске
make up                            # пересборка изменившихся образов + перезапуск
make logs                          # убедиться: Flyway применил новые миграции
```

- `make logs` при старте пишет строки вида `Migrating schema "public" to version "2 - baseline"` — это подтверждение, что схема приехала.
- **Миграции никогда не переписываются задним числом** — только добавляются новые файлы `V<N>__<имя>.sql`. Flyway хранит контрольные суммы: переписал уже применённую миграцию — у коллеги backend упадёт при старте с ошибкой checksum mismatch.
- **Демо/тестовые данные делим только сид-миграциями** (SQL-файлы в `db/migration`), а не руками через UI в свою базу — иначе локальные базы разработчиков разбегутся.
- Ориентировочное время пересборки: только фронтенд — секунды; Java-код — до минуты; новая зависимость в `pom.xml` — минуты (скачивание).

## 6.2. Асинхронная работа (разные графики)

Разное время работы — норма, процесс этого не требует одновременного присутствия:

- **SLA на ревью:** PR ревьюится вторым разработчиком в течение рабочего дня, а не «когда встретимся».
- **Self-merge** разрешён для мелких правок (docs, типы, форматирование) с пометкой в описании PR — чтобы мелочи не ждали ревью сутки.
- **Длинная ветка:** если feature-ветка живёт больше одного дня — ежедневно вливать в неё свежий `develop`, чтобы конфликт не рос снежным комом.
- **Вместо «синка 15 минут в день»** (Часть 9) при несовпадающих графиках — короткий письменный статус в общий чат: что сделано, что блокирует, какие контрактные решения приняты.
- **Контракт-first:** если живого эндпоинта ещё нет, Dev A работает по `shared/openapi.yaml` (генерация типов через `npm run generate-api`) и мокам, а не ждёт бэкенд. Убежавший вперёд бэкенд — не проблема: эндпоинты уже в `develop`, фронт проверяет на живых данных.

## 6.3. Ритуал AI-ассистента (начало и конец каждой сессии)

AI-ассистент, работающий над задачей сессии, обязан:

**В начале сессии — актуализировать копию и окружение:**
1. `git fetch origin && git status` — сверка: нет ли незакоммиченных/неотмеченных изменений с прошлой сессии. Если есть — показать владельцу и оформить отдельным коммитом (ничего не теряем и ничего «мусорного» не тащим).
2. `git checkout develop && git pull` — актуальная версия кода с GitHub.
3. `make up && make logs` — окружение в Docker поднято из актуального кода, миграции применены (см. 6.1).

**В конце сессии — сверить файлы и отправить в Git:**
1. `git status --short` — сверка всех изменившихся файлов: ничего не забыто; мусор (дампы, скриншоты, временные файлы) в коммит не попадает; `.env` не коммитится никогда.
2. Тесты зелёные: `mvn -B verify -f backend/pom.xml` (backend) / `npm --prefix frontend run build` (frontend).
3. Commit (Conventional Commits, метка `[S<номер>]`) → push → PR → merge по правилам Части 6.
4. Обновить статус сессии в плане (см. 6.4) — в составе того же PR, что и работа сессии.

## 6.4. План и прогресс — в репозитории, один файл

- Чек-лист лежит в **`docs/plan/GUSTO_B2B_Final_Checklist.md`** — единственный экземпляр, источник истины для обоих разработчиков и AI-ассистентов.
- **Дублирующих копий не заводим** (в т.ч. «личных» для каждого разработчика): два файла гарантированно разойдутся после первого же параллельного редактирования, и появится вопрос «какой правильный». Один файл + Git = у обоих всегда одинаковый план.
- **Прогресс отмечается в том же файле:** исполнитель после merge добавляет под заголовком сессии строку
  `> ✅ Выполнено ГГГГ-ММ-ДД [A|B|AB] — <одна строка: что фактически сделано>`
  в составе того же PR, что и работа сессии.
- Одновременные отметки двух сессий конфликтом не являются — в PR остаются обе.
- Правки самого плана (новые решения, уточнения) — тем же порядком: ветка → PR → merge, версия файла в шапке увеличивается.

---

# ЧАСТЬ 7. ИНСТРУМЕНТЫ И ОКРУЖЕНИЕ

## 7.1. Установка один раз

```bash
# Dev-машина (macOS)
brew install openjdk@21 node@20 docker colima bruno
# IntelliJ IDEA (backend), VS Code (frontend)
```

## 7.2. Структура репозитория

```
gusto-b2b/
├── backend/            # Spring Boot
├── frontend/           # React + Vite
├── shared/openapi.yaml # контракт API
├── bruno/              # API-коллекции (в Git!)
├── infra/              # docker-compose, nginx
├── docs/               # decisions.md, runbooks.md, 1c-export-format.md, import-templates.md
│   └── brandbook/      # 6 страниц бренд-бука (JPEG) — источник дизайн-токенов
└── Makefile
```

## 7.3. Bruno-коллекции

- `bruno/auth/` — login, refresh, me, 2fa.
- `bruno/catalog/`, `bruno/orders/`, `bruno/documents/`, `bruno/admin/`.
- Каждая сессия с эндпоинтами = обновление коллекции + коммит.

---

# ЧАСТЬ 8. ДАННЫЕ И НАСТРОЙКИ (блокирующих вопросов нет)

Всё, что заказчик не может дать сейчас, **не блокирует разработку** — делается через настройки системы и демо-данные:

| Что | Решение |
|---|---|
| Реквизиты продавца | Сид — демо «ЧТУП ЛорСан» из примера ТТН. Заменяются в админке: **Настройки → Реквизиты** (S38). PDF берут реквизиты из настроек на момент выпуска. |
| Контакты сайта (телефон, email) | Демо `+375 29 123-45-67` / `info@gustomeat.by` из лендинга-образца. Заменяются в **Настройки → Сайт**. |
| Прайс-лист | Мы сами задаём шаблон импорта xlsx (`docs/import-templates.md`, S35): SKU, название, категория, бренд, цена, ед.изм. Заказчик потом просто заполняет шаблон. |
| Остатки из 1С | Тот же шаблонный подход: SKU, склад, количество. |
| Экспорт в 1С | Стандартный набор колонок (заказ: номер, дата, УНП, контрагент, позиции, суммы, НДС). Колонки настраиваются в **Настройки → Интеграции** без доработки кода. |
| Логотипы/фото | Placeholder-заглушки. Замена через модуль `files` в админке (S17). |
| Печать/подпись в PDF | Поля-заглушки «М.П.» / подпись. Картинки загружаются позже в настройках. |

**Принцип: ни один внешний материал не останавливает сессии.**

---

# ЧАСТЬ 9. БЫСТРЫЙ СТАРТ (завтра)

1. Создать GitHub-репозиторий, пригласить второго разработчика.
2. Выполнить S01 (репозиторий + CI) — полдня.
3. Параллельно: Dev A → S03 (frontend-скелет), Dev B → S02 (backend-скелет).
4. Синк 15 минут в день (при несовпадающих графиках — письменный статус, см. 6.2); каждая сессия завершается PR.
