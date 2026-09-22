# Полировка: разрядка, hero-плитка, 404, пустые состояния

Written against: c6947a1

## Evidence chain

- Surface: кнопки/бейджи (типографика), главная витрина (hero-плитка товара), 404, кабинет юрлица (документы), каталог кабинета (placeholder массового добавления)
- Problem: (1) разрядка кнопок/бейджей 0.08em (~1.1 px) против контрактных 4–8 px для Oswald (`_tokens.scss:10` — «прописные, разрядка 4–8px»); (2) hero-плитка товара на главной — emoji 🥩 как «фото» (`HomePage.tsx:80-90`); (3) 404-кнопка делает `window.location.href` — полная перезагрузка вместо роутинга (`NotFoundPage.tsx:10`); (4) пустое состояние документов юрлица «Счетов пока нет» без подсказки (у менеджера подсказка есть — непоследовательность); (5) placeholder массового добавления ссылается на несуществующие SKU «KOLO-001, SOS-025» (`CabinetCatalogPage.tsx`).
- Design evidence: бренд-бук стр. 5 (типографика: Oswald «всегда прописные, разрядка 4–8 px»); UiKit как визуальный эталон; формат placeholder'ов витрины
- Owner: `Button.module.scss`, `Badge.module.scss`, `Tabs.module.scss`, `HomePage.tsx`, `NotFoundPage.tsx`, `CabinetDocumentsPage.tsx`, `CabinetCatalogPage.tsx`
- Scope and affected surfaces: все потребители Button/Badge/Tabs; перечисленные страницы
- Uncertainty: разрядка 4–8 px на маленьких кнопках может вести к переносу длинных подписей («ЗАКАЗЫ ПОСТАВЩИКАМ») — проверить крайние случаи, допустимо зафиксировать отклонение в токенах.

## Design decision

Поднять letter-spacing кнопок/бейджей/табов до 0.18em (≈2.5px при 14px) — заметный шаг к бренд-контракту без риска переносов; зафиксировать в комментарии токенов как принятое отклонение от 4–8px для UI-кнопок (бренд-разрядка остаётся крупным заголовкам/меню). Заменить emoji на фирменный плейсхолдер (круг-клеше с бордо, как логотип-марка), 404 перевести на `<Link>`, досинхронизировать тексты пустых состояний и пример SKU.

## Reuse

- Токены шрифтов; `PublicHeader` `logo__mark` — визуал круга-клеше для плейсхолдера
- `LinkButton` из plan-07 (если принят) или `<Link>` для 404

## Changes

1. `frontend/src/components/ui/Button/Button.module.scss:10`, `Badge/Badge.module.scss:8`, `Tabs/Tabs.module.scss`
   - Change: `letter-spacing: 0.18em`; comment в `_tokens.scss`: «UI-кнопки/бейджи: 0.18em — осознанное отклонение от 4–8px (переносы на sm)»
   - Verify: длинные подписи («СБРОСИТЬ ПАРОЛЬ», «ЗАКАЗЫ ПОСТАВЩИКАМ») не переносятся на 2 строки при 1440px; визуальная сверка UiKit
2. `frontend/src/pages/public/HomePage.tsx:80-90`
   - Change: emoji-блок → плейсхолдер в стиле ProductCard (нейтральная подложка + знак/аббревиатура) либо первая картинка реального товара из `/hits`; текст «Свежий стейк на кости» и цена сохраняются
   - Verify: на 1440px/390px плитка выглядит как карточка каталога, без эмодзи
3. `frontend/src/pages/NotFoundPage.tsx:10`
   - Change: `window.location.href` → `useNavigate()`/`<Link to="/">`; кнопка остаётся визуально
   - Verify: переход SPA-навигацией, без перезагрузки
4. `frontend/src/pages/cabinet/CabinetDocumentsPage.tsx`
   - Change: пустое состояние → «Счетов пока нет — счёт выставит менеджер после подтверждения заказа» (накладные — аналогично)
   - Verify: текст виден на обеих вкладках
5. `frontend/src/pages/cabinet/CabinetCatalogPage.tsx` (placeholder массового добавления)
   - Change: пример → реальные артикулы: «Например: bedro-kurinoye, steyk-ribay»
   - Verify: введённый пример из placeholder валиден и находит товары

## Scope

- Inherit: все кнопки/бейджи/табы получают разрядку автоматически
- Verify: список склада, таблицы админки — не поехали заголовки колонок
- Exclude: редизайн hero-секции, настоящие фото товаров (нет в сидах), rest типографики заголовков

## Validation

- Product: просмотр главной, 404-переход, юрлицо открывает пустые документы — тексты помогают дальнейшему шагу
- Interface: кнопки sm в таблицах не переносятся; плитка hero на 390px
- System: `grep -rn "letter-spacing" frontend/src/components/ui` — значения только 0.18em (или иное согласованное); эмодзи в HomePage отсутствует
- Repository: `npm --prefix frontend run lint && npm --prefix frontend run test && npm --prefix frontend run build` → зелёные (снапшот-тесты текстов обновить)

## Stop conditions

- Stop, если при 0.18em обнаружатся переносы в критичных таблицах — откатить до 0.12em и зафиксировать решение в токенах.

## Design documentation

- `docs/decisions.md`: «UI-кнопки: letter-spacing 0.18em (отклонение от бренд-бука 4–8px, причина — переносы в таблицах)» — после приёмки владельцем.
