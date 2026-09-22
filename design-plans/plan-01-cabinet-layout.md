# Единый layout кабинета клиента с навигацией

Written against: c6947a1

## Evidence chain

- Surface: все маршруты `/cabinet/*` (дашборд, каталог, корзина, заказы, документы, профиль)
- Problem: навигация существует только на `/cabinet/catalog` (собственная мини-шапка с несистематизированными ссылками, часть — с дефолтно-синим цветом браузера); на `/cabinet/cart` и `/cabinet/profile` навигации нет вовсе; на дашборде и в заказах — 2 нестилизованные ссылки. Со страницы корзины нельзя уйти ничем, кроме кнопки «Назад» браузера; на профиле нет даже «Выйти».
- Design evidence: скриншоты аудита `design-plans/ui-usability-audit-2026-09-22.md` (разделы «Кабинет клиента»); бренд-бук: шапка — бордо с кремовым текстом; контраст с витриной (`PublicLayout`/`PublicHeader`) и складом (`WarehouseLayout`), где шапка есть.
- Owner: `frontend/src/App.tsx` (маршруты), `frontend/src/components/public/PublicHeader.tsx` (образец шапки), `frontend/src/components/admin/AdminLayout.tsx` (образец layout-with-Outlet)
- Scope and affected surfaces: `/cabinet`, `/cabinet/catalog`, `/cabinet/cart`, `/cabinet/orders`, `/cabinet/documents`, `/cabinet/profile`; косвенно — тесты `AuthGuards`
- Uncertainty: состав пунктов меню подтверждается матрицей ролей 2.1 (документы — только CUSTOMER_LEGAL); счётчик корзины — из кэша react-query, может отставать на чужих вкладках (приемлемо).

## Design decision

Создать `CabinetLayout` (компонент с `<Outlet/>`) и шапку в бренд-стиле: бордовая полоса, логотип ГУСТО (ссылка на `/cabinet`), пункты: **Каталог**, **Корзина** (со счётчиком позиций), **Заказы**, **Документы** (только для CUSTOMER_LEGAL), **Профиль**, действие **Выйти** и ссылка **Витрина** (`/`). Все страницы `/cabinet/*` переводятся под этот layout; локальные мини-шапки и разрозненные ссылки-кнопки удаляются. Это устраняет «запертые» страницы и даёт кабинету ту же модель навигации, что у витрины/склада/админки.

## Reuse

- `AdminLayout.tsx` — паттерн layout с `<Outlet/>` и активным пунктом меню
- `PublicHeader.tsx` — стилистика бордовой шапки (фон `$color-bordeaux`, кремовый текст, Oswald uppercase)
- `useAuthStore` (`user.role`), `useCartStore` не нужен: счётчик серверной корзины берётся из react-query-кэша `["cart"]` (уже инвалидируется при мутациях, см. `CabinetCartPage.tsx:79`)
- Токены `styles/_tokens.scss`; стили ссылок — по образцу `PublicHeader.module.scss` (не синие дефолтные)

## Changes

1. `frontend/src/components/cabinet/CabinetLayout.tsx` (новый) + `CabinetLayout.module.scss`
   - Change: шапка (бордо, логотип→`/cabinet`, NavLink-пункты с активным состоянием, «Выйти» с `loading`-состоянием — стилем, как в `PublicHeader`), `<main><Outlet/></main>`
   - Preserve: правила доступа не меняются —<RoleGuard'ы остаются в App.tsx
   - Verify: на каждом из 6 маршрутов кабинета видна одна и та же шапка; активный пункт подсвечен
2. `frontend/src/App.tsx:92-108`
   - Change: вложить маршруты `/cabinet/*` в `<Route element={<CabinetLayout/>}>`; маршруты заказов/документов остаются под своими RoleGuard
   - Preserve: редиректы и guards как есть
   - Verify: прямые ссылки `/cabinet/orders` и т.д. работают под layout
3. `frontend/src/pages/cabinet/CabinetCatalogPage.tsx:207-222`
   - Change: удалить локальную мини-шапку (логотип, счётчик, «Кабинет», «Выйти»)
   - Preserve: таблицу, массовое добавление, пагинацию
   - Verify: навигация доступна из шапки layout'а; дублей «Выйти» нет
4. `frontend/src/pages/cabinet/CabinetDashboardPage.tsx`
   - Change: убрать кнопку-ссылку «Выйти» и дубли навигации; плитки-действия оставить (переходят на LinkButton из plan-07 либо временно остаются)
   - Preserve: приветствие и роль
   - Verify: «Выйти» есть только в шапке
5. `frontend/src/pages/cabinet/CabinetOrdersPage.tsx:109-110`, `CabinetDocumentsPage.tsx:102-104`, `CabinetCartPage.tsx:206`
   - Change: удалить локальные ссылочные наборы «Каталог/Корзина/Заказы» (замена — шапка)
   - Verify: нет синих дефолтных ссылок
6. Счётчик корзины в шапке
   - Change: `useQuery(["cart"])`-данные из кэша → «Корзина (N)»; N скрывать при 0
   - Preserve: не создавать лишний запрос: использовать те же `getCart` и `staleTime`
   - Verify: после «В корзину» в каталоге счётчик в шапке растёт

## Scope

- Inherit: все страницы кабинета получают шапку автоматически
- Verify: менеджер/админ, заходящие в `/cabinet` по истории, видят кабинет без поломок; выход из шапки очищает корзину/стора как раньше (`PublicHeader.handleLogout` — образец)
- Exclude: витрина, менеджер, склад, админка; редизайн контента страниц

## Validation

- Product: клиент добавляет 2 товара и оформляет заказ, ни разу не использовав «Назад» браузера
- Interface: 6 маршрутов × роли LEGAL/INDIVIDUAL; mobile 390px (пункты переносятся, не наезжают); состояние «корзина пуста»
- System: в коде не осталось вторых «Выйти» и мини-шапок: `grep -rn "Выйти" frontend/src/pages/cabinet` → 0 вхождений
- Repository: `npm --prefix frontend run lint && npm --prefix frontend run test && npm --prefix frontend run build` → зелёные

## Stop conditions

- Stop, если матрица 2.1 запрещает физлицу видеть пункт «Заказы» (сейчас `/cabinet/orders` — оба типа клиентов): тогда уточнить состав меню у владельца.

## Design documentation

- В `docs/decisions.md`: «Кабинет клиента — единый CabinetLayout с шапкой (навигация вне страниц)».
