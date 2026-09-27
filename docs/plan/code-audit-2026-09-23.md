# Кодовый аудит бэкенда и фронтенда — 2026-09-23

Read-only аудит против `develop` @ `7ac0845` (рабочее дерево чистое). Метод: три независимых
прохода (безопасность бэка, корректность/качество бэка, качество фронта) + ручное ревью трёх
свежайших UI-коммитов (`fd8db00`, `85685eb`, `7ac0845`), не покрытых прошлыми аудитами.
Известные/исправленные вещи (hotfix 2026-09-02, S18.4, S40, хвост S41) не дублируются.

## P1 — чинить в первую очередь

### Бэкенд

1. **Sequences на 2027 год: 1 января создание заказов и складских документов упадёт.**
   Миграции создают `order_seq_2026` (V14), `doc_seq_warehouse_*_2026` (V10), `doc_seq_purchase_2026`
   (V11) только на 2026 год, а `SequenceRotationJob.rotate()`
   (`backend/.../outbox/job/SequenceRotationJob.java:27-43`) гарантирует лишь инвойсные и ТН-серии.
   01.01.2027 `nextval('order_seq_2027')` → «sequence does not exist» → 500 на каждом создании
   заказа/документа/заказа поставщику. Фикс: дополнить `rotate()` idempotent-созданием всех
   годовых sequence (`CREATE SEQUENCE IF NOT EXISTS`) + тест «первый день года».

2. **Деактивация пользователя не останавливает доступ.**
   `JwtAuthenticationFilter.java:44-53` строит аутентификацию из JWT, не проверяя
   `userDetails.isEnabled()`; `RefreshTokenService.rotate` не проверяет `active`/`deletedAt`;
   `AdminUserService.setActive(false)` (`:93-95`) не ревокает refresh-токены (в отличие от
   `deleteUser`). Деактивированный пользователь бесконечно обновляет токены и работает под
   старой ролью (до 15 мин access-TTL — бесконечно из-за refresh). Фикс: проверка в фильтре и
   в `rotate()`, revoke при деактивации.

3. **Гонка в регистрации платежей → переплата.**
   `PaymentService.java:56-82`: `sumByInvoiceId()` → проверка остатка → вставка — read-modify-write
   без блокировки инвойса. Два параллельных платежа оба проходят проверку и вместе превышают долг;
   статус считается от устаревшей суммы. Фикс: `@Lock(PESSIMISTIC_WRITE)` на чтение инвойса в этом
   пути (или атомарный UPDATE с условием по сумме).

### Фронтенд

4. **Корзина витрины для залогиненного пользователя теряется и рассинхронизирована.**
   `ProductCard.tsx:60-72`, `ProductPage.tsx:74-89`, `PublicHeader.tsx:26-34` — «в корзину» с
   публичной витрины всегда пишет в localStorage-cartStore даже для залогиненных; мерж в серверную
   корзину есть только на LoginPage/RegisterPage. Итог: в кабинете товара нет, счётчики в шапке
   витрины и кабинета разные, при logout `clear()` безвозвратно удаляет добавленное.
   Фикс: при наличии `user` писать сразу через `putCartItem` (+инвалидация `["cart"]`).

## P2 — серьёзно

### Бэкенд: безопасность

5. **Rate limit логина обходится спуфингом X-Forwarded-For; per-account lockout отсутствует.**
   `AuthController.clientIp()` (`:177-183`) берёт первый (клиент-контролируемый) элемент XFF,
   ключ лимита — ip+email. Вращая XFF, можно брутфорсить один аккаунт (и 2FA в `AuthService.login`).
   То же в `SiteRequestController.java:77-80`. Фикс: доверять XFF только от своего прокси
   (последний hop) + лимит/lockout по email с backoff.
6. **Неограниченный page size в каталоге.** `CatalogService.java:60-63`,
   `CabinetCatalogService.java:50-53`, `ProductService.java:37` — `size` из запроса уходит в
   `PageRequest.of` без cap (остальные списки clamp 1..100); `size=1000000` на permitAll-ручке →
   тяжёлая выборка + N+1 → DoS. Фикс: clamp 1..100.
7. **Idempotency-key не скоупится по пользователю** (`IdempotencyService.java:39-50`,
   `OrderController.java:47-51`): lookup по key+endpoint+hash без userId — при совпадении чужого
   ключа и тела клиент получит чужой заказ в ответе. Фикс: фильтровать по userId.
8. **XLSX-экспорт не скоупится для MANAGER** (`XlsxExportService.java:47-98` + AdminExportController):
   менеджер выгружает заказы/счета/накладные всех компаний, вразрез с матрицей «менеджер видит
   своих клиентов» (`findAllVisibleTo`). Фикс: для MANAGER фильтр по закреплённым компаниям.
9. **CRM-заметки: горизонтальный IDOR между менеджерами** (`CrmWorkService.java:101-119`):
   `notes(companyId)`/`addNote` без проверки закрепления, в отличие от задач и OwnershipAspect.
10. **Публичная форма заявок без @Size** (`SiteRequestDtos.java:13-26`); заодно
    `OrderDtos.CreateRequest` (deliveryAddress/note) и `@Positive` quantity без верхней границы
    (`OrderDtos.java:47-49,108-111`).

### Бэкенд: корректность/надёжность

11. **OutboxPoller: self-invocation отменяет транзакционность** (`OutboxPoller.java:63-82`):
    `@Scheduled scheduled()` вызывает `this.pollOnce()` мимо прокси → `@Transactional` не
    применяется, `FOR UPDATE SKIP LOCKED` работает в autocommit (блокировки мгновенно сняты),
    при втором инстансе — дубли доставки. Фикс: выборка в отдельном `@Transactional`-бине /
    TransactionTemplate; доставку (HTTP/SMTP) держать вне tx.
12. **Внешние вызовы без таймаутов.** `TelegramApiClient.java:34-39` — `RestClient.create()` на
    каждое сообщение, таймаутов нет (бесконечные); `spring.mail` без connection/read/write timeout.
    Зависший Telegram/SMTP блокирует цикл доставки. Фикс: один бин RestClient с таймаутами 3-10 c +
    mail-таймауты в `application.yml`.
13. **Статус-машины: check-then-act без блокировки.** Двойной confirm черновика
    (`WarehouseDocumentService.java:90-110`, аналогично `OrderLifecycleService.takeInWork/changeStatus`,
    `PurchaseOrderService.registerReceipt`) даёт двойные складские движения. Фикс: атомарный
    `UPDATE ... SET status=:next WHERE id=? AND status=:prev` + проверка affected rows
    (для takeInWork — `WHERE manager_id IS NULL`).
14. **WaybillService fallback создания sequence мёртвый** (`:307-321`): после неудачного `nextval`
    tx aborted — `create sequence` в той же транзакции гарантированно падает. Создание серий до
    ротации джобой = 500. Фикс: `REQUIRES_NEW`/предварительное создание в SequenceRotationJob.
15. **AuditService.append отравляет транзакцию** (`AuditService.java:23-34`): catch после ошибки
    вставки не спасает — tx aborted, падает вся бизнес-операция с невнятной ошибкой.
    Фикс: `REQUIRES_NEW` или честный ретрой.
16. **Нет маппинга DataIntegrityViolationException** (`GlobalExceptionHandler.java:52-57`):
    гонки `InvoiceService.createFromOrder` и `AuthService.register` отдают 500 вместо 409.
    Фикс: обработчик по имени констрейнта (`ux_invoices_order_active` и др.).
17. **N+1 на списках и каталоге кабинета**: позиции заказов/документов догружаются запросом на
    строку (`OrderService.java:179`, `InvoiceService.java:209-219`, `WaybillService.java:170-178`,
    `WarehouseDocumentService.java:179`, `PurchaseOrderService.java:108,183`, `CrmWorkService.java:132-137`);
    каталог кабинета — ~3 запроса на товар (`CabinetCatalogService.java:94-103`), ~100 запросов на
    страницу из 20. Фикс: batch `IN`-запросы / EntityGraph по образцу уже сделанного для цен.
18. **Окно гонки идемпотентности POST /orders** (`OrderController.java:47-54`,
    `IdempotencyService.java:53-72`): два параллельных запроса с одним ключом → два заказа.
    Фикс: резервирование ключа (уникальная in-progress строка) до создания заказа.
19. **Refresh rotate без атомарного revoke** (`RefreshTokenService.java:48-80`): параллельный
    refresh одним токеном обходит reuse-detection. Фикс: атомарный
    `UPDATE ... SET revoked=true WHERE token_hash=? AND revoked=false` + affected rows.

### Фронтенд

20. **Мутации корзины кабинета не инвалидируют `["cart"]`** (`CabinetCartPage.tsx:76-83,142-152`
    против `CabinetLayout.tsx:19`): бейдж «Корзина (N)» в шапке кабинета зависает после
    изменения количества/очистки (соседние страницы инвалидировали правильно). Фикс:
    `queryClient.invalidateQueries({ queryKey: ['cart'] })` после `putCartItem`/`clearCart`.
21. **Raw-useEffect поиск: запрос на каждый keystroke + гонка неупорядоченных ответов**
    (`WarehouseBalancePage.tsx:26-38`, `ManagerOrdersPage.tsx:52-71`,
    `ManagerOrderCreatePage.tsx:36-42`): нет debounce и AbortController. Фикс: debounce 300 мс +
    `signal`, или перевести на react-query.
22. **Мерж корзины при логине глотает ошибки и всё равно чистит локальные позиции**
    (`LoginPage.tsx:59-64`): неперенесённая позиция молча теряется. Фикс: чистить только
    перенесённое, toast о неперенесённом.
23. **Ручной тип `Order` расходится с `schema.d.ts`** (`api/cart.ts:29-52`): `status: string`
    плодит `as OrderStatus` по всему UI (`ManagerOrdersPage.tsx:139,158`, `CabinetOrdersPage.tsx:80,152`
    и др.). Фикс: `export type Order = components["schemas"]["Order"]` по образцу adminCatalog.ts.
24. **Бэк-офисные списки тихо обрезаются до 50** (`DocumentsPage.tsx:68`, `CabinetOrdersPage.tsx:37`):
    фильтры применяются по первым 50 записям, пагинации/индикатора нет. Фикс: серверная фильтрация +
    Pagination, или индикатор «показаны первые 50».
25. **Тела запросов склада типизированы `unknown`** (`api/warehouse.ts:134-165`): compile-time
    защиты нет при наличии ручных интерфейсов. Фикс: `*Request`-типы из schema.d.ts.

## P3 — мелочи (списком)

- Telegram webhook: секрет допустим в query (попадёт в access-логи), сравнение не constant-time
  (`TelegramWebhookController.java:62-67`) — только заголовок + `MessageDigest.isEqual`.
- Роль из JWT-claims, не из БД (подхватывается за ≤15 мин) — приемлемо, связано с P1-2.
- Планировщики без межинстансных локов (ShedLock) — ок для single-instance, починить до масштабирования.
- Общий CircuitBreaker на все каналы outbox (сбой Telegram блокирует email); poison-сообщения
  FAILED без реанимации/метрик (`OutboxPoller.java:40-46,94,111-120`).
- XlsxImportService: N+1 по SKU (`:88,130,175`), мёртвая ветка валидации (`:163-167`),
  потеря cause в catch (`:286-292`); `findAllByDeletedAtIsNull` грузит весь каталог при archiveMissing.
- Файлы пишутся на диск до коммита tx — сироты при rollback (Waybill/InvoicePdf/XlsxImport/XlsxExport);
  периодическая сверка каталога с `files` или afterCommit.
- Rate limit: check-then-act окно; при недоступном Redis auth/экспорт отдают 500 (решить осознанно:
  fail-open для лимитов).
- `mime` файла <2 байт берётся из client Content-Type (`FileService.java:126-131`) — практической
  опасности нет.
- Дублирование `vatIncluded` (CartService/InvoiceService/WaybillService) — вынести в хелпер;
  пустой `@EntityGraph(attributePaths = {})` (`ProductRepository.java:25`); нет
  `hibernate.jdbc.batch_size`, hikari `connection-timeout`/`leak-detection-threshold`.
- `adminMocks.ts` (фейковые ПДн) компилируется в прод-бандл — вынести в test-only/динамический импорт.
- `key={index}` на удаляемых строках форм (WarehouseDocumentsPage:295, WarehousePurchaseOrdersPage:265,
  AdminSettingsPage:392) — сброс фокуса при удалении.
- Дублирование роль→дашборд (LoginPage:67-74 vs dashboardByRole.ts); `applySeo` не подключён
  (document.title не обновляется на витрине); «повторить заказ» перезаписывает количество в корзине;
  `downloadPdf` — raw fetch без refresh-логики; setTimeout без cleanup (ResetPasswordPage:52,
  CabinetProfilePage:71); `initChat()` в теле рендера App.tsx:53; `/ui-kit` без guard в проде.

## Ревью свежих коммитов (fd8db00, 85685eb, 7ac0845) — вручную

Валидация: vitest 9/9 (Select 5, QuantityStepper 4), eslint 0 errors, tsc --noEmit чисто.

- **QuantityStepper**: клампинг/округление корректны, busy-защита в корзине синхронная (гонки
  двойного коммита нет). Находки:
  - P3: в корзине `step={0.001}` захардкожен для всех позиций, включая штучные (каталог использует
    `weightStep ?? 1`) → степпер даёт «1.001 шт». Унифицировать по weightStep.
  - P3: `CabinetCatalogPage.handleRowQuantityChange` использует `Number(value)` без замены запятой
    (в корзине для этого `parseQuantityInput`): «1,5» на мобильных/Safari → NaN → пустой инпут и
    тихое добавление 1.
  - P3: после успешного добавления `rowQuantities` сбрасывается в 0, поле показывает «0», повторный
    клик добавит 1 (`0 || 1`) — неожиданная семантика для пользователя.
- **Select (кастомный дропдаун)**: ARIA combobox-паттерн корректный, RHF-шим рабочий (15 мест
  использования, `required` и вызовов `preventDefault` на событиях нет; z-index 60 внутри
  stacking context модалей — перекрытий нет). Находки:
  - P3: popup всегда раскрывается вниз без flip и репозиционирования — у нижней границы вьюпорта
    и внутри модалок с внутренним скроллом может обрезаться/уйти за экран; typeahead (ввод буквы)
    не поддерживается — заметно на длинных списках (категории, пользователи).
- **fd8db00 (компакт-контролы, Table mobileHidden)**: ревью чисто — API колонки обратно совместим,
  data-label карточный вид на мобильных консистентен.

## Проверено, чисто (кратко)

- **Бэк/безопасность**: filter chain и CORS (явный allowlist, ничего админского в permitAll), JWT/refresh
  (ротация + reuse-detection, SHA-256, secure-random), bcrypt + pgcrypto-seed, OwnershipAspect,
  серверные цены, инъекций нет (параметризованные запросы, захардкоженные сортировки, UUID-storageKey
  против path traversal), файлы (magic-byte allowlist, 10MB, sanitized Content-Disposition),
  одноразовые токены email/reset, отсутствие стектрейсов в ответах.
- **Бэк/корректность**: StockService с `FOR UPDATE` и корректными инвариантами, атомарность
  бизнес-запись+outbox, POI/стримы закрываются, BigDecimal везде (scale 2, HALF_UP), 0 `@Autowired`-полей,
  нет TODO/FIXME в main, констрейнт `ux_invoices_order_active`, отмена заказа освобождает резерв в одной tx.
- **Фронт**: токены — access только в памяти, refresh в httpOnly-куке; single-flight refresh в client.ts;
  0 вхождений dangerouslySetInnerHTML/innerHTML, CMS-контент экранируется; 0 console/any/ts-ignore;
  роль-guards — UX-слой, бэк-403 обработан; react-query-инвалидации на admin-страницах; zod-валидация форм;
  confirm на деструктиве; ObjectURL освобождается.

## Открытый хвост (known-only, вне скоупа этого аудита)

S41: CSP/HSTS на Nginx и блокирующий dependency-scan (нужен NVD_API_KEY) — ждут прод-инфраструктуры.
