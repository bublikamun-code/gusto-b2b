# Интерактивные элементы: семантика и доступность

Written against: c6947a1

## Evidence chain

- Surface: витрина (шапка, главная, карточка/страница товара), кабинеты, менеджер (дашборд), админка (товары, CMS), склад (модалки документов)
- Problem: (1) `<Link>` оборачивает `<Button>` в 10+ местах — невалидный HTML, двойной tab-стоп, непредсказуемое поведение скринридеров; (2) переключатели «ХИТ»/«НОВИНКА» в админке — голые `<button>` вокруг `Badge`: без cursor/hover/aria-pressed, неотличимы от статических бейджей; (3) иконки «✕» удаления строки в складских модалках без aria-label, строка позиций вылезает за край модалки, селект товара сжат до стрелки; (4) три страницы используют кастомные overlay-модалки без Escape/focus-trap (проверено рантаймом: Escape не закрывает «Детали заказа»).
- Design evidence: `Button.tsx`/`Modal.tsx` — корректные паттерны уже в системе (aria-label у ✕ Modal, Escape у Modal); инвентаризация аудита (разделы 2–4 сводки)
- Owner: `components/public/PublicHeader.tsx`, `pages/public/HomePage.tsx`, `ProductPage.tsx`, `pages/cabinet/*`, `pages/manager/ManagerDashboardPage.tsx`, `pages/admin/AdminDashboardPage.tsx`, `AdminProductsPage.tsx`, `WarehouseDocumentsPage.tsx`, `WarehousePurchaseOrdersPage.tsx`, `CabinetOrdersPage.tsx`, `CrmClientsPage.tsx`, `AdminCmsPage.tsx`
- Scope and affected surfaces: перечисленные файлы; новая примитивная обёртка `LinkButton`
- Uncertainty: нет

## Design decision

(1) Ввести `LinkButton` — `react-router` `<Link>`, стилизованный классами `Button.module.scss` (та же визуальная система, но валидный одиночный интерактив) — и заменить им все вложенные Link>Button. (2) Бейджи-переключатели — обычная `<button>` с классом toggle (cursor, hover-кайма, `aria-pressed`, title). (3) «✕» в строках позиций — aria-label «Удалить позицию» + починка сетки строки (элементы внутри модалки, без переполнения). (4) Кастомные оверлеи перевести на UI `Modal` (Escape/focus бесплатно).

## Reuse

- `Button.module.scss` — источник классов для `LinkButton` (композиция: `Link className={[buttonStyles.button, buttonStyles[variant], buttonStyles[size]]}`)
- `Modal.tsx` — база для перевода трёх кастомных модалок
- Exemplar семантической кнопки-состояния: `Pagination.tsx` (aria-label, disabled)

## Changes

1. `frontend/src/components/ui/LinkButton/LinkButton.tsx` (новый; экспорт из `components/ui/index.ts`)
   - Change: props `to, variant, size, block, children` → `<Link>` с классами Button
   - Preserve: внешние `<a>` не трогаем (tel:/mailto: остаются ссылками)
   - Verify: один tab-стоп на действие; визуально неотличимо от Button
2. Замена вложенных интерактивов: `PublicHeader.tsx:74-78`; `HomePage.tsx:64-73,154-158`; `ProductPage.tsx:30-32`; `CabinetDashboardPage.tsx:30-36`; `ManagerDashboardPage.tsx:30-48`; `AdminDashboardPage.tsx:78-82`; плитки дашборда админа (`AdminDashboardPage.tsx:140-148` — Link поверх div оставить, добавив hover-подсветку и `aria-label` с текстом плитки)
   - Verify: `grep -rn -A2 "<Link" frontend/src/pages frontend/src/components | grep -B1 "<Button"` → 0
3. `AdminProductsPage.tsx:204-208`
   - Change: кнопку дать класс `styles.toggle` (cursor:pointer; hover: обводка бордо; focus-visible ring) + `aria-pressed={row.isHit}` + `title="Показывать как ХИТ на витрине"`; бейдж остаётся внутри как визуал
   - Preserve: PATCH-мутация и optimistic-логика
   - Verify: клавиатурой Tab→Enter переключает флаг; скринридер announces pressed state
4. Складские модалки: `WarehouseDocumentsPage.tsx:258-318`, `WarehousePurchaseOrdersPage.tsx:250-288`
   - Change: ✕-кнопкам `aria-label="Удалить позицию"`; сетке `.itemRow` — фиксированные роли колонок (Select flex 2 1 40%, числа flex 1, ✕ auto), перенос на новую строку при 390px; удалить отрицательные margin'ы, вызывающие вылезание за край
   - Verify: строка позиций целиком внутри модалки на 1440px и 390px; ✕ озвучивается
5. Кастомные модалки → UI Modal: `CabinetOrdersPage.tsx:143-199` (детали заказа), `CrmClientsPage.tsx:94-99` (карточка клиента), `AdminCmsPage.tsx:147-165` (редактор страницы)
   - Change: обёртка `<Modal open onClose title>`; контент и кнопки сохранить; Escape/фокус — из Modal
   - Verify: Escape закрывает все три (проверить рантаймно); фокус возвращается на открывшую кнопку

## Scope

- Inherit: потребители LinkButton получают единый вид кнопок-ссылок
- Verify: не задеть реальные `<a href>` внешние и табы
- Exclude: полный a11y-аудит (контрасты, aria-live) — вне объёма

## Validation

- Product: с клавиатуры пройти шапку витрины (Корзина — один Tab), переключить «ХИТ» в админке, удалить позицию в складской модалке, открыть/закрыть «Детали заказа» по Escape
- Interface: 1440px и 390px; тач-цели «✕» ≥ 32×32
- System: вложенных интерактивов нет (grep из п.2); все модалки — на UI Modal
- Repository: `npm --prefix frontend run lint && npm --prefix frontend run test && npm --prefix frontend run build` → зелёные

## Stop conditions

- Stop, если верстка складских строк окажется переиспользуемой компонентой с другими потребителями — тогда править общую компоненту, а не копии.

## Design documentation

- `docs/decisions.md`: «Ссылка-кнопка — только LinkButton; вложенные интерактивные элементы запрещены; модалки — только UI Modal».
