# Блокировка кнопок на время мутаций (loading/disabled)

Written against: c6947a1

## Evidence chain

- Surface: все мутирующие кнопки приложения (~45 без состояния против ~34 с ним)
- Problem: кнопки, запускающие сетевые мутации, остаются активными до ответа: двойной клик = двойной заказ/двойная проводка/повторный импорт; пользователь не видит, что действие выполняется. Особый случай: «Очистить корзину» (`CabinetCartPage.tsx:187-194`) делает `await clearCart()` без try/catch — ошибка сети приводит к unhandled rejection без уведомления.
- Design evidence: `Button.tsx:29` уже поддерживает `loading` (disabled + спиннер) — компонентный механизм есть, страницы его не прокидывают; в `ManagerOrdersPage` образцовый паттерн `busy === row.id`
- Owner: страницы-потребители (ниже); паттерн-образец `ManagerOrdersPage.tsx:123-131`
- Scope and affected surfaces: витрина, кабинет, менеджер, склад, админка
- Uncertainty: нет

## Design decision

Провести существующий механизм `Button loading` через все мутации: локальный `busy`-стейт (или `isPending` react-query, где мутации уже на нём) → `loading={busy}` на кнопке. Плюс обёртка ошибок: каждый `await` мутации — try/catch с `push(message, "error")`. Никакая мутация не должна быть способна отправиться дважды из-за второго клика.

## Changes

1. Шапки/дашборды — «Выйти»: `PublicHeader.tsx:64`, `AdminLayout.tsx:53`, `CabinetDashboardPage.tsx:38` (после plan-01 — только шапка кабинета)
   - Change: `const [busy,setBusy]=useState(false)`; в `handleLogout` setBusy(true) в начале, finally(false)
   - Verify: во время logout кнопка со спиннером, повторный клик невозможен
2. Витрина: `ProductCard.tsx:56`, `ProductPage.tsx:70-88`, `CatalogPage.tsx:99`
   - Change: «В корзину» — `loading` на время `addItem`/`putCartItem`; «Повторить» — `loading={isLoading}` и disabled
   - Verify: спам-клик по «В корзину» даёт одну позицию с кол-вом N (или N последовательных инкрементов без ошибок) и без гонок
3. Кабинет: `CabinetCartPage.tsx:125,184,187`, `CabinetCatalogPage.tsx:196,241`, `CabinetOrdersPage.tsx:126`
   - Change: «Убрать»/«В корзину»/«Добавить список» — busy per-row/per-action; «Очистить корзину» — busy + try/catch + toast об ошибке (+ ConfirmModal из plan-02)
   - Preserve: «Подтвердить заказ» уже имеет loading — не трогать
   - Verify: сетевой трейс: двойной клик «Очистить корзину» даёт один DELETE
4. Менеджер/склад: `DocumentsPage.tsx:140,144-154,181-191`, `WarehouseDocumentsPage.tsx:175,178`, `WarehousePurchaseOrdersPage.tsx:175,179`, `WarehouseSuppliersPage.tsx:119`
   - Change: per-row `busy===row.id` (паттерн ManagerOrders); PDF-кнопки — `loading` на время скачивания (`downloadPdf` возвращает promise — обернуть)
   - Verify: клик «Провести» блокирует обе кнопки строки до ответа
5. CRM: `CrmClientsPage.tsx:138` («Добавить» заметку — loading + уже есть disabled по пустому тексту)
6. Админка: `AdminUsersPage.tsx:287,290`, `AdminCompaniesPage.tsx:272`, `AdminCmsPage.tsx:115,120`, `AdminDashboardPage.tsx:36-38`, `AdminAuditPage.tsx:122`
   - Change: loading через mutation.isPending / busy-стейт; «Найти» — `loading={loading}` вместо `disabled={loading}`
   - Preserve: у Users/Companies submit-кнопки модалок loading уже есть
7. Общий ревью-критерий
   - Change: `grep -rn "onClick={async" frontend/src/pages` — для каждой найденной кнопки убедиться, что она либо имеет loading, либо не делает сетевых вызовов
   - Verify: ревью-чеклист пуст

## Scope

- Inherit: все потребители Button — поведение через props, не через CSS
- Verify: страницы, где loading уже есть, не регрессируют (Login, Reset, BecomeClient, ManagerOrders, CrmInbox/Leads, интеграционный мастер)
- Exclude: оптимистичные апдейты, скелетоны загрузки данных (не в этом плане)

## Validation

- Product: оформить заказ на «медленной сети» (throttling в DevTools): кнопки становятся неактивными, дубликаты невозможны
- Interface: спиннер виден на всех размерах кнопок (sm/md/lg) и не ломает высоту строки таблицы
- System: паттерн един — busy-стейт + `loading` проп; параллельного «disabled»-костыля через CSS нет
- Repository: `npm --prefix frontend run lint && npm --prefix frontend run test && npm --prefix frontend run build` → зелёные; Vitest: cartStore-логика не меняется, добавить тест на helper busy-логики, если выделится в util

## Stop conditions

- Stop, если при подключении loading обнаружится серверная неидемпотентность конкретного эндпоинта (двойной POST создаёт сущности) — тогда сначала зафиксировать баг бэкенда в плане/тикет, не маскировать UI-блокировкой.

## Design documentation

- `docs/decisions.md`: «Все мутирующие кнопки обязаны блокироваться на время запроса (Button loading); await без try/catch+toast запрещён».
