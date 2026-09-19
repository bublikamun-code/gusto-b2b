import { expect, test } from "@playwright/test";

/**
 * Витрина в мобильном вьюпорте (S39, приёмка: розница приходит с телефона).
 * Проект mobile-chrome даёт 375px; этот же файл в проекте chromium проверяет
 * десктоп. Проверяем ключевые публичные страницы: контент загрузился,
 * горизонтального скролла нет.
 */

const PAGES: { path: string; expectText: RegExp }[] = [
  { path: "/", expectText: /каталог/i },
  { path: "/catalog", expectText: /каталог|товар/i },
  { path: "/delivery", expectText: /доставк/i },
  { path: "/contacts", expectText: /контакт|телефон|минск/i },
];

for (const { path, expectText } of PAGES) {
  test(`витрина ${path} читаема и без горизонтального скролла`, async ({ page }) => {
    await page.goto(path);

    await expect(page.getByText(expectText).first()).toBeVisible();
    await expect(page.getByText(/ошибка сервера|не удалось загрузить/i)).toHaveCount(0);

    // горизонтальный скролл на мобильном — типичный признак «поехавшей» вёрстки
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow, `${path}: горизонтальный скролл ${overflow}px`).toBeLessThanOrEqual(2);
  });
}

test("карточка товара открывается с витрины", async ({ page }) => {
  await page.goto("/catalog");
  const card = page.getByText(/р\.|кг|десяток/i).first();
  await expect(card).toBeVisible();
  // первый товар → на страницу товара (/products/:sku)
  await page.locator("a[href*='/products/']").first().click();
  await expect(page.getByText(/в корзину|количество|цена/i).first()).toBeVisible();
});
