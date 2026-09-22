# Подтверждение деструктивных и необратимых действий

Written against: c6947a1

## Evidence chain

- Surface: склад (`/warehouse/documents`, `/warehouse/purchase-orders`, `/warehouse/suppliers`), документы (`/manager/documents`, `/admin/documents`), CRM (`/manager/leads`), CMS/товары/интеграция/пользователи/компании (`/admin/*`), корзина и профиль кабинета
- Problem: 12+ необратимых действий выполняются в один клик: «Провести», «Отменить» (документ), «Отправить»/«Отменить» (заказ поставщику), «Создать и отправить», «Деактивировать», «Выпустить» (счёт), «В архив», «Потерян» (лид), удаление фото, «Применить импорт» (с опцией архивации отсутствующих), «Очистить корзину», смена пароля. Подтверждение есть только у «Удалить» пользователя/компании — через нативный `window.confirm` вне дизайн-системы.
- Design evidence: бренд-запрет на системные диалоги не формализован, но `UiKitPage.tsx:189-213` демонстрирует целевой паттерн подтверждения через `Modal` («Подтверждение заказа»); нативный confirm визуально чужой и блокирует поток
- Owner: `frontend/src/components/ui/Modal/Modal.tsx` (база), страницы-потребители (список ниже)
- Scope and affected surfaces: перечисленные страницы; компонентная библиотека `components/ui`
- Uncertainty: нет

## Design decision

Добавить `ConfirmModal` поверх существующего `Modal` (single responsibility: заголовок, текст, кнопки «Отмена»/подтверждение, состояние `loading`, семантика `danger` — бордовая заливка кнопки подтверждения) и провести им все деструктивные/необратимые действия. Нативные `window.confirm` заменить. Это закрывает риск случайных необратимых операций одним переиспользуемым компонентом вместо пер-страничных решений.

## Reuse

- `Modal.tsx` (Escape, aria, ✕) — единственная база; новых примитивовBesides ConfirmModal не требуется
- Токены кнопок: `Button variant="primary"` + новый модификатор `danger` (фон `$color-bordeaux` уже есть; для danger достаточно оставить primary — текст «Отменить/Удалить» сам по себе красный смысл не несёт; решение: НЕ вводить красный цвет — вне палитры; danger = primary + формулировка)
- Exemplar: `UiKitPage.tsx:189-213`

## Changes

1. `frontend/src/components/ui/ConfirmModal/ConfirmModal.tsx` (новый)
   - Change: props `open, title, children (текст), confirmLabel, cancelLabel="Отмена", loading, onConfirm, onClose`; Enter=подтвердить, Escape/✕/клик по фону=отмена (наследуется от Modal); фокус на «Отмену» при открытии
   - Preserve: стилистика Modal
   - Verify: в UiKit добавить демо-блок ConfirmModal (danger и обычный)
2. Склад: `WarehouseDocumentsPage.tsx:175,178`, `WarehousePurchaseOrdersPage.tsx:175,179,286`, `WarehouseSuppliersPage.tsx:119`
   - Change: «Провести», «Отменить», «Отправить», «Деактивировать» — через ConfirmModal с текстом последствия («Провести документ №X — сформируются движения склада»); «Создать и отправить» → разделить: «Создать черновик» и отдельная отправка черновика с confirm
   - Preserve: per-row loading добавляется планом-03
   - Verify: каждое действие требует второго клика; Escape отменяет
3. Документы: `DocumentsPage.tsx:140`
   - Change: «Выпустить» — ConfirmModal («Счёт станет неизменяемым: снапшоты зафиксируются»)
   - Verify: черновик не выпускается одним кликом
4. CRM: `CrmLeadsPage.tsx:100`
   - Change: «Потерян» — ConfirmModal (финальный статус)
   - Verify: случайный клик не закрывает лид
5. Админка: `AdminProductsPage.tsx:100` (удаление фото), `AdminCmsPage.tsx:115,120` (публикация/архив), `AdminIntegrationPage.tsx:174,203` (импорт; в тексте явно «отсутствующие в файле будут архивированы», если флаг включён), `AdminUsersPage.tsx:228,290` и `AdminCompaniesPage.tsx:221,272` (заменить `window.confirm` на ConfirmModal)
   - Verify: `grep -rn "window.confirm" frontend/src` → 0
6. Кабинет: `CabinetCartPage.tsx:187` («Очистить корзину»), `CabinetProfilePage.tsx:107` (смена пароля: «Все сессии будут завершены»)
   - Verify: корзина не очищается, пароль не меняется без подтверждения
7. Менеджер: `ManagerOrdersPage.tsx:147` — confirm только при выборе статуса «Отменён»

## Scope

- Inherit: все перечисленные потоки получают единый UX подтверждения
- Verify: «Сбросить пароль» (админ) остаётся без confirm — выдача временного пароля не деструктивна
- Exclude: undo-механизмы, история действий

## Validation

- Product: менеджер создаёт складской документ, отменяет его — требуется подтверждение; случайный клик мимо — документ цел
- Interface: каждый ConfirmModal с клавиатуры (Tab/Enter/Escape); на мобильной ширине текст не обрезается
- System: `grep -rn "window.confirm" frontend/src` → пусто; ConfirmModal — единственный механизм подтверждения
- Repository: `npm --prefix frontend run test && npm --prefix frontend run build` → зелёные; добавленные Vitest на ConfirmModal (open/loading/confirm-callback)

## Stop conditions

- Stop, если владельцы решат, что складским операторам подтверждения мешают (скоростной ввод): тогда оставить confirm только для CANCELLED/архивации/импорта.

## Design documentation

- `docs/decisions.md`: «Деструктивные действия — только через ConfirmModal; window.confirm запрещён».
