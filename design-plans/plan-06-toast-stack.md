# Тосты: положение, слои, различимость

Written against: c6947a1

## Evidence chain

- Surface: всё приложение (тосты используются на каждой мутирующей странице)
- Problem: (1) стек тостов прибит к `top: 1rem` — на страницах с шапкой тост ложится ПОВЕРХ бордовой шапки и перекрывает её элементы («Вход выполнен» поверх шапки кабинета); (2) на «Документах» тост ошибки оказался визуально ЗА кнопками шапки страницы (полускрыт) — пользователь может не увидеть причину отказа; (3) success и error различаются только лёгкой заливкой фона — обе с бордовой полосой слева; на бегу неразличимы.
- Design evidence: скриншоты аудита (вход в кабинет, документы менеджера); `Toast.module.scss` (`.stack` top/right/z-index 200; `.success`/`.error` — оба `border-left-color: $color-bordeaux`)
- Owner: `frontend/src/components/ui/Toast/Toast.module.scss`, `Toast.tsx`
- Scope and affected surfaces: все потребители `useToast`
- Uncertainty: нет

## Design decision

Опустить стек ниже фиксированных шапок (top: 4.5rem, right: 1rem), поднять z-index выше контента страниц (шапки страниц бэк-офиса имеют высокие z-index — стек должен быть над всем: 1000), и развести семантику цветом полосы: success — графит, error — бордо + тонированный фон (сохранить), info — желток; добавить ведущий глиф (✓ / ✕ / i) для цветослепых пользователей. Компактность: max-width уже 22rem — сохранить, на мобильных — `left: 1rem` вместо фиксированной ширины.

## Reuse

- Существующие классы `.toast/.success/.error/.info` — меняются только стили и разметка глифа
- Токены (`$color-bordeaux`, `$color-graphite`, `$color-yolk`)
- Exemplar: UiKit «Toast: успех/ошибка/инфо» — обновить демо после правки

## Changes

1. `frontend/src/components/ui/Toast/Toast.module.scss`
   - Change: `.stack { top: 4.5rem; right: 1rem; z-index: 1000 }`; медиа ≤640px: `left: 1rem; right: 1rem; top: 4rem`; `.toast` — `box-shadow` НЕТ (запрет теней) — отделить от фона границей `1px solid $color-line`
   - Preserve: анимации появления, стек с gap
   - Verify: тост не перекрывает шапку, виден НАД контентом «Документов»
2. `frontend/src/components/ui/Toast/Toast.tsx`
   - Change: рендерить глиф перед текстом: success «✓», error «✕», info «i» (текстом, `aria-hidden`, стилизованным цветом полосы)
   - Preserve: API `push(message, type)` не меняется
   - Verify: три типа различимы при ч/б зрении
3. `frontend/src/pages/UiKitPage.tsx:174-182`
   - Change: демо-кнопки остаются; визуальная приёмка трёх типов после правки
   - Verify: скриншот-сверка с бренд-буком (без новых цветов)

## Scope

- Inherit: все `useToast`-вызовы автоматически
- Verify: страницы с фиксированной шапкой (витрина, кабинеты, админка) и без неё (auth) — тост не наезжает на поля форм на 390px
- Exclude: позиционирование тостов по центру, очередь/авто-дисмисс тайминги (оставить как есть)

## Validation

- Product: в кабинете выполнить вход (тост поверх cream-фона, шапка видна); в менеджере выставить счёт заказу физлица (ошибка полностью видна поверх контента)
- Interface: 1440px/390px; длинный текст (60+ символов) переносится; три тоста подряд складываются в стек
- System: z-index стекa больше, чем у любых шапок (`grep -rn "z-index" frontend/src --include=*.scss | sort` — сверить)
- Repository: `npm --prefix frontend run build` → зелёные

## Stop conditions

- Stop, если дизайнер решит перенести тосты в нижний правый угол — правка одна и та же (координаты .stack), но согласовать до имплементации.

## Design documentation

- None (стилевая правка в рамках UiKit-приёмки).
