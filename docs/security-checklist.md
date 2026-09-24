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
| CSP / HSTS на витрине | ⏳ S41 (конфиг готов) | `infra/nginx/gustomeat.conf` — ждёт деплоя на VPS |

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
| OWASP dependency-check + `mvn versions:display-dependency-updates` | ✅ информационно | CI job `dependency-scan` (continue-on-error), версия плагина зафиксирована на 12.1.8 |
| Перевести dependency-scan в блокирующий режим | ⏳ S41 | нужен секрет `NVD_API_KEY` |
| CVE с CVSS ≥ 9 в backend-зависимостях | ⏳ S41 | 7 уникальных CVE без опубликованных фиксов — см. «Незакрытые CVE» ниже |

## Секреты

| Пункт | Статус | Где |
|---|---|---|
| Секреты только через .env (не в git); gitleaks в CI | ✅ | S01, `.env.example` |
| Токен Telegram хранится в settings БД, в API не возвращается (write-only) | ✅ | S38 |
| JWT_SECRET обязателен, без дефолта | ✅ | `application.yml` |

## Незакрытые CVE (CVSS ≥ 9, без фиксов), 2026-09-24

Полный OWASP-скан backend на Spring Boot 3.5.16 + Netty 4.1.138 + Tomcat 10.1.60
оставляет 7 уникальных CVE с CVSS ≥ 9 в четырёх артефактах. Все закрытые ранее
CVE (Spring Boot 3.5.0, Netty, Tomcat) сняты обновлением parent до 3.5.16 и
override'ами версий в `backend/pom.xml`.

| Артефакт | CVE | Фикс |
|---|---|---|
| `spring-core`, `spring-web` 6.2.19 | CVE-2026-47884, -47890, -47891, -47892, -59313 (9.8), -59283 (9.1) | нет в Maven Central: 6.2.20 не опубликован, вся ветка 6.2.x помечена affected |
| `spring-security-core`, `spring-security-web` 6.5.11 | CVE-2026-59270 (9.1) | нет: вся ветка 6.5.x affected, фикс только в 7.x (несовместимая мажорная) |

Действия: следить за выходом Spring Framework 6.2.20+ / Security 6.5.12+ и
обновить `backend/pom.xml`; до этого `dependency-scan` остаётся
информационным (`continue-on-error`), а не блокирующим.

## Прочее

| Пункт | Статус | Где |
|---|---|---|
| Метод-безопасность по ролям (матрица 2.1) + ownership-аспект | ✅ | S10, `@PreAuthorize` |
| CSRF выключен осознанно: stateless JWT, cookie — только refresh (SameSite=Strict) | ✅ | `SecurityConfig` |
| Валидация всех входных DTO (`@Valid`) | ✅ | по коду |
| Негативные тесты прав (401/403/IDOR) | ✅ | интеграционные тесты S10+ |

Итог: чек-лист пройден; открытые пункты (CSP/HSTS на Nginx, блокирующий
dependency-scan с NVD_API_KEY, CVE без фиксов) переносятся на S41 — они зависят
от прод-инфраструктуры и релизов Spring.
