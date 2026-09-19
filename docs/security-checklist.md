# Чек-лист безопасности (S40)

Приёмка S40 — «чек-лист безопасности пройден». Фиксируется состояние на
2026-09-19; пересматривается при каждом изменении аутентификации/файлов.

## Аутентификация и пароли

| Пункт | Статус | Где |
|---|---|---|
| Пароли только bcrypt (`gen_salt('bf')` / BCryptPasswordEncoder) | ✅ | V1 seed, `SecurityConfig` |
| JWT access 15 мин, короткоживущий | ✅ | `application.yml` (access-ttl-minutes) |
| Refresh — httpOnly Secure SameSite=Strict, ротация + reuse detection | ✅ | `RefreshTokenService`, S08 |
| Новый логин отзывает прежние refresh-токены (одна активная сессия) | ✅ | `RefreshTokenService.create` |
| 2FA (TOTP + recovery codes) для ADMIN/ACCOUNTANT, опционально | ✅ | S10.1 |
| Гейт подтверждения email саморегистрации (включается в S42) | ✅ | `auth.require_email_confirmation`, S08.1 |
| Восстановление пароля по хэшированному одноразовому токену | ✅ | S08 |

## Rate limiting

| Пункт | Статус | Где |
|---|---|---|
| login 5/15 мин (email+IP) | ✅ | `AuthController.checkRateLimit`, S08 |
| register / password-reset / email-resend | ✅ | там же, S08 |
| Заявки с сайта 10/час на IP | ✅ | `SiteRequestService`, S31 |
| Экспорт 1С 10/час на пользователя | ✅ | `AdminExportController` + `RequestRateLimiter`, S40 |
| Импорт 1С 10/час на пользователя (предпросмотр 30/час) | ✅ | `AdminImportController`, S40 |
| Идемпотентные ключи на создание заказа | ✅ | `IdempotencyService`, S20 |

## Заголовки и CORS

| Пункт | Статус | Где |
|---|---|---|
| `X-Content-Type-Options: nosniff` | ✅ | `SecurityConfig.headers`, S40 |
| `X-Frame-Options: DENY` | ✅ | там же |
| `Referrer-Policy: strict-origin-when-cross-origin` | ✅ | там же |
| CORS-allowlist: только `APP_CORS_ORIGINS` (по умолчанию dev-фронт) | ✅ | `SecurityConfig.corsConfigurationSource`, S40 |
| CSP / HSTS на витрине | ⏳ S41 | Nginx + Let's Encrypt (`docker-compose.prod.yml`) |

## Файлы и документы

| Пункт | Статус | Где |
|---|---|---|
| Ключи файлов — случайные UUID, не предсказуемые пути | ✅ | `FileStorageService`/UUID v4 |
| PRIVATE-файлы: только владелец/админ (проверка на скачивании) | ✅ | `FileService.download` |
| Не-изображения отдаются `attachment` (не исполняются на домене) | ✅ | `FileController.download` |
| Имя файла в Content-Disposition очищается от CRLF/кавычек | ✅ | там же |
| PDF счетов/накладных — приватные файлы владельца | ✅ | S25/S26 |

## Логи (нет PII)

| Пункт | Статус | Где |
|---|---|---|
| Email получателя маскируется (`a****@domain`) | ✅ | `EmailSender`, S40 |
| Payload outbox-сообщений не логируется (заявки: имя/телефон/email) | ✅ | `LoggingOutboxChannel`, S40 |
| Токен Telegram не попадает в логи; тело ответа бота не логируется | ✅ | `TelegramApiClient` (S40), токен — write-only в настройках (S38) |
| Пароли нигде не логируются | ✅ | grep по `log.*password` — пусто |
| audit_log пишется в БД, не в логи | ✅ | `AuditService` |

## Зависимости

| Пункт | Статус | Где |
|---|---|---|
| `npm audit --omit=dev --audit-level=high` (blocking) | ✅ | CI frontend job |
| OWASP dependency-check + `mvn versions:display-dependency-updates` | ✅ информационно | CI job `dependency-scan` (continue-on-error) |
| Перевести dependency-scan в блокирующий режим | ⏳ S41 | нужен секрет `NVD_API_KEY` |

## Секреты

| Пункт | Статус | Где |
|---|---|---|
| Секреты только через .env (не в git); gitleaks в CI | ✅ | S01, `.env.example` |
| Токен Telegram хранится в settings БД, в API не возвращается (write-only) | ✅ | S38 |
| JWT_SECRET обязателен, без дефолта | ✅ | `application.yml` |

## Прочее

| Пункт | Статус | Где |
|---|---|---|
| Метод-безопасность по ролям (матрица 2.1) + ownership-аспект | ✅ | S10, `@PreAuthorize` |
| CSRF выключен осознанно: stateless JWT, cookie — только refresh (SameSite=Strict) | ✅ | `SecurityConfig` |
| Валидация всех входных DTO (`@Valid`) | ✅ | по коду |
| Негативные тесты прав (401/403/IDOR) | ✅ | интеграционные тесты S10+ |

Итог: чек-лист пройден; открытые пункты (CSP/HSTS на Nginx, блокирующий
dependency-scan с NVD_API_KEY) переносятся на S41 — они зависят от прод-инфраструктуры.
