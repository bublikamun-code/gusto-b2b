# Бренд бэк-офиса и витрины: системные элементы → токены

Written against: c6947a1

## Evidence chain

- Surface: кабинет (шапка каталога, ссылки на страницах), менеджер (`/manager/orders`, `/manager/inbox`, CRM-страницы), документы, склад (фильтры), админка (интеграция, настройки)
- Problem: (1) нестилизованные `<Link>` рендерятся дефолтным синим цветом браузера с подчёркиванием (скриншоты аудита); (2) нативные `<select>` и `<input>` мимо UI-компонентов выглядят системными; (3) хром бэк-офиса держит цвета вне палитры через CSS-переменные с fallback'ами: `#6b7280` (18 вхождений), `#e5e5e0`, `#f6f6f4`, `#8c1d1d` (не-бордо!), `#1a1a1a`, `#555555` — переменные нигде не определены; (4) валюта разная: витрина «р./кг», кабинет/бэк-офис «Br»; (5) кнопка «В корзину» на витрине — бордо, хотя бренд-бук (`_tokens.scss:6`, ценник бренд-бука) и UiKit отводят ей жёлтый акцент.
- Design evidence: `_tokens.scss` («Значения не менять без сверки с бренд-буком»; «не добавляем новые цвета»), `docs/brandbook/` стр. 4–5 (цвета, типографика, ценник «р./кг»), `UiKitPage.tsx:79` («В корзину» = accent)
- Owner: `frontend/src/pages/**/*.module.scss`, `frontend/src/lib/format.ts`, `frontend/src/components/public/ProductCard.tsx`
- Scope and affected surfaces: все страницы бэк-офиса + карточки витрины; НЕ трогает брендовые `Button/Badge/Table` (они уже на токенах)
- Uncertainty: единица валюты («Br» vs «р.») — гейт решения владельца; план следует рекомендации бренд-бука «р.»

## Design decision

Свести хром бэк-офиса к четырём брендовым токенам: вторичный текст — графит с прозрачностью, границы — `$color-line`, подложки — сливки/карта, «акцент» ссылок/активных состояний — бордо (не `#8c1d1d`); все поля ввода — через UI-компоненты `Select/Input/Textarea`; ссылки-навигация — стилизованные (по образцу `PublicHeader.module.scss`); формат денег — один `formatMoney`; «В корзину» на витрине — accent. Серо-белые значения и синие ссылки исчезают.

## Reuse

- Токены `styles/_tokens.scss` (включая производные `$color-card`, `$color-line`)
- `components/ui/Select`, `Input`, `Textarea` — уже стилизованы (проверено в UiKit)
- Образец стиля ссылок: `PublicHeader.module.scss` (`.nav__link`, hover-переходы)
- Exemplar компонентной страницы: `CabinetCatalogPage` (таблица на бренд-компонентах)

## Changes

1. Палитра хрома (все перечисленные `.module.scss`)
   - Files: `components/admin/AdminLayout.module.scss`, `pages/admin/AdminPages.module.scss`, `AdminSettingsPage.module.scss`, `pages/cabinet/CabinetCartPage.module.scss`, `CabinetOrdersPage.module.scss`, `CabinetProfilePage.module.scss`, `pages/documents/DocumentsPage.module.scss`, `pages/manager/Crm*.module.scss`, `ManagerOrdersPage.module.scss`, `ManagerOrderCreatePage.module.scss`, `pages/public/BecomeClientPage.module.scss`, `CmsPageView.module.scss`, `pages/warehouse/WarehousePages.module.scss`
   - Change: замены: `#6b7280`/`#555555` → `rgba($color-graphite, 0.72)`; `#9ca3af` → `rgba($color-graphite, 0.55)`; `#d1d5db`/`#e5e5e0` → `$color-line`; `#f6f6f4` → `$color-cream`; `#ffffff`/`#fff` (подложки) → `$color-card`; `#8c1d1d` → `$color-bordeaux`; `#b91c1c`/`#b45309` (ошибки полей) → `$color-bordeaux`/`$color-yolk-dark`; `#1a1a1a` → `$color-graphite`; var(--…)-обёртки удалить (или определить переменные из токенов в `index.scss`, если команда хочет их сохранить)
   - Preserve: размеры, отступы, сетки — не трогать
   - Verify: `grep -rn "#6b7280\|#8c1d1d\|#e5e5e0\|#f6f6f4\|#b91c1c\|#b45309\|#555555\|#1a1a1a\|#9ca3af\|#d1d5db" frontend/src --include=*.scss` → 0
2. Системные ссылки → брендовые
   - Files: `CabinetOrdersPage.tsx:109-110`, `CabinetDocumentsPage.tsx:102-104` (исчезают после plan-01), `ManagerOrdersPage.tsx:92`, `CrmInboxPage.tsx:128-130`, `ManagerDashboardPage.tsx` (кнопки остаются, но их Link-обёртки стилизуются plan-07)
   - Change: класс со стилем `nav__link` (Oswald uppercase, кремовый на бордо или бордо на сливках по контексту страницы) — вынести общий `.pageLink` в общий scss-файл страниц бэк-офиса
   - Verify: `grep -rn "styles.headerActions\|<Link" pages/manager` — ссылки имеют классы, цвет — из токенов
3. Нативные поля → UI-компоненты
   - Files: `AdminIntegrationPage.tsx:139,231` (select'ы), `CrmClientsPage.tsx:132` (input), `DocumentsPage.tsx:296,302` (input'ы), `AdminSettingsPage.tsx:227,315` (checkbox'ы — обернуть в label со стилем токенов, чекбокс bordeaux accent-color)
   - Preserve: логика форм не меняется
   - Verify: на перечисленных страницах нет голых `<select`/`<input` без UI-обёртки (кроме checkbox, оставленных по решению)
4. Валюта (после подтверждения гейта; по умолчанию — бренд-бук)
   - Files: `lib/format.ts`, витринные карточки (`ProductCard.tsx`, `ProductPage.tsx`, `HomePage.tsx`), потребители formatMoney
   - Change: `formatMoney` → суффикс « р.»; единицы — «р./кг», «р./уп»; удалить локальные подстановки «Br»
   - Preserve: точность и локализацию чисел (запятая)
   - Verify: одна и та же цена выглядит одинаково на витрине и в кабинете
5. «В корзину» → accent
   - Files: `components/public/ProductCard.tsx:56` (secondary→accent), `pages/public/ProductPage.tsx:70-88` (primary→accent)
   - Preserve: размеры/иконок нет — только вариант
   - Verify: карточка и страница товара соответствуют UiKit («В корзину» — жёлтая)

## Scope

- Inherit: все страницы бэк-офиса получают палитру из токенов автоматически (правки в scss)
- Verify: контраст кремового текста на бордо и графита на сливках (WCAG AA для крупного текста); тёмные фото в CMS-превью
- Exclude: логотип/фавикон, PDF-документы, email-шаблоны, статусные семантические цвета (отдельный гейт)

## Validation

- Product: пройти витрина → кабинет → менеджер → склад → админка: ни одного синего/системного элемента, цены в одном формате
- Interface: все затронутые страницы на 1440px и 390px; hover-состояния ссылок; поля с ошибками читаемы
- System: `grep` из п.1 → 0; ни одного нового hex-цвета вне `_tokens.scss`
- Repository: `npm --prefix frontend run lint && npm --prefix frontend run test && npm --prefix frontend run build` → зелёные (Vitest на formatMoney обновить под «р.»)

## Stop conditions

- Stop, если владелец выберет «Br» — тогда п.4 меняет суффикс на «Br» (вся механика та же); не делать два формата параллельно.

## Design documentation

- `docs/decisions.md`: «Бэк-офис на бренд-токенах; новые hex-цвета в scss запрещены; формат денег единый».
