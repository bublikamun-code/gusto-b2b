import { expect, test, type Page } from "@playwright/test";
import { ADMIN, apiLogin, listInvoices, registerPayment } from "../helpers/api";

/**
 * Полный бизнес-цикл (S39, приёмка): регистрация клиента админом → вход →
 * каталог → заказ → счёт → ТТН → оплата.
 *
 * Компания и пользователь заводятся через UI админки; временный пароль клиента
 * читается из модалки «Сбросить пароль» (он показывается один раз); покупки
 * клиента — через UI кабинета. Бэкенд допускает только одну активную сессию на
 * пользователя (S08: новый логин отзывает старые refresh-токены), поэтому
 * админский API-логин для платежа делается ПОСЛЕ всех UI-шагов админа, а
 * проверка «Оплачен» — после повторного UI-входа админа.
 */

async function loginViaUi(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Пароль").fill(password);
  await page.getByRole("button", { name: /войти/i }).click();
  await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 });
}

test("регистрация клиента админом → вход → каталог → заказ → счёт → ТТН → оплата", async ({
  browser,
}) => {
  const suffix = Date.now();
  const clientEmail = `e2e-client-${suffix}@gustomeat.by`;
  const companyName = `ООО «E2E Цикл ${suffix}»`;
  const unp = String(191000000 + (suffix % 999999));

  // ---- 1. Регистрация клиента админом: компания + пользователь (UI) ----------
  const adminCtx = await browser.newContext();
  const adminPage = await adminCtx.newPage();
  await loginViaUi(adminPage, ADMIN.email, ADMIN.password);

  await adminPage.goto("/admin/companies");
  await adminPage.getByRole("button", { name: "Добавить компанию" }).click();
  await adminPage.getByLabel("Название", { exact: true }).fill(companyName);
  await adminPage.getByLabel("УНП").fill(unp);
  await adminPage.getByLabel("Юридический адрес").fill("г. Минск, ул. E2E, 1");
  await adminPage.getByRole("button", { name: "Создать" }).click();
  await expect(adminPage.getByText("Компания создана")).toBeVisible();

  await adminPage.goto("/admin/users");
  await adminPage.getByRole("button", { name: "Добавить пользователя" }).click();
  await adminPage.getByLabel("Email").fill(clientEmail);
  await adminPage.getByLabel("ФИО").fill("E2E Клиент Юрлицо");
  await adminPage.getByLabel("Роль", { exact: true }).selectOption("CUSTOMER_LEGAL");
  await adminPage
    .getByLabel("Компания")
    .selectOption({ label: `${companyName} (УНП ${unp})` });
  await adminPage.getByRole("button", { name: "Создать" }).click();
  await expect(adminPage.getByText("Пользователь создан")).toBeVisible();

  // временный пароль выдаётся отдельно (S06): сбрасываем в UI и читаем из модалки
  const clientRow = adminPage.locator("tr", { hasText: clientEmail }).first();
  await clientRow.getByRole("button", { name: "Сбросить пароль" }).click();
  await expect(
    adminPage.getByText("Скопируйте пароль — он показывается один раз."),
  ).toBeVisible();
  const temporaryPassword = await adminPage
    .locator("input[readonly]")
    .first()
    .inputValue();

  // ---- 2. Вход клиента (UI) ---------------------------------------------------
  const clientCtx = await browser.newContext();
  const clientPage = await clientCtx.newPage();
  await loginViaUi(clientPage, clientEmail, temporaryPassword);

  // ---- 3. Каталог: клиент-юрлицо кладёт товар в корзину -----------------------
  await clientPage.goto("/cabinet/catalog");
  const addToCart = clientPage.getByRole("button", { name: /в корзину/i }).first();
  await expect(addToCart).toBeVisible({ timeout: 15_000 });
  await addToCart.click();

  // ---- 4. Корзина → оформление заказа ----------------------------------------
  await clientPage.goto("/cabinet/cart");
  await clientPage.getByLabel("Имя получателя").fill("E2E Получатель");
  await clientPage.getByLabel("Телефон получателя").fill("+375290000000");
  await clientPage.getByLabel("Получение").selectOption("PICKUP");
  await clientPage.getByRole("button", { name: "Подтвердить заказ" }).click();
  await clientPage.getByText(/Заказ .* принят/).waitFor({ state: "visible", timeout: 15_000 });
  const acceptedText = await clientPage
    .getByRole("heading")
    .filter({ hasText: /принят/ })
    .textContent();
  const orderNumber = acceptedText?.match(/([А-ЯA-Z]-\d{4}-\d{4,6})/)?.[1] ?? "";
  expect(orderNumber, `номер заказа из подтверждения: "${acceptedText}"`).not.toBe("");

  // ---- 5. Счёт: админ выставляет из заказа и выпускает -----------------------
  await adminPage.goto("/admin/documents");
  await adminPage.getByRole("button", { name: "Выставить счёт из заказа" }).click();
  const invoiceOrderSelect = adminPage
    .getByRole("dialog", { name: "Выставить счёт из заказа" })
    .getByRole("combobox");
  await invoiceOrderSelect
    .locator("option")
    .filter({ hasText: orderNumber })
    .waitFor({ state: "attached", timeout: 15_000 });
  const invoiceOptions = await invoiceOrderSelect.locator("option").allTextContents();
  const invoiceLabel = invoiceOptions.find((text) => text.includes(orderNumber));
  if (!invoiceLabel) throw new Error(`Заказ ${orderNumber} не найден в модалке счёта`);
  await invoiceOrderSelect.selectOption({ label: invoiceLabel });
  await adminPage.getByRole("button", { name: "Создать счёт" }).click();
  const draftToast = await expect(
    adminPage.getByText(/Счёт .* создан \(черновик\)/),
  ).toBeVisible().then(() =>
    adminPage.getByText(/Счёт .* создан \(черновик\)/).first().textContent(),
  );
  const invoiceNumber = draftToast?.match(/СЧ-\d+/)?.[0] ?? "";
  expect(invoiceNumber, "номер счёта из тоста").not.toBe("");

  const invoiceRow = adminPage.locator("tr", { hasText: invoiceNumber }).first();
  await expect(invoiceRow).toBeVisible();
  await invoiceRow.getByRole("button", { name: "Выпустить" }).click();
  await expect(adminPage.getByText(/выпущен, PDF готов/).first()).toBeVisible();

  // ---- 6. ТТН: оформляем по тому же заказу ------------------------------------
  await adminPage.getByRole("button", { name: "Оформить накладную" }).click();
  const waybillOrderSelect = adminPage
    .getByRole("dialog", { name: "Оформить накладную" })
    .getByRole("combobox")
    .first();
  const waybillOptions = await waybillOrderSelect.locator("option").allTextContents();
  const waybillLabel = waybillOptions.find((text) => text.includes(orderNumber));
  if (!waybillLabel) throw new Error(`Заказ ${orderNumber} не найден в модалке накладной`);
  await waybillOrderSelect.selectOption({ label: waybillLabel });
  await adminPage
    .getByRole("dialog", { name: "Оформить накладную" })
    .getByRole("combobox")
    .nth(1)
    .selectOption("TTN");
  await adminPage.getByPlaceholder(/Автомобиль/).fill("AB 1234-5");
  await adminPage.getByRole("button", { name: "Оформить", exact: true }).click();
  await expect(adminPage.getByText(/Накладная .* оформлена/)).toBeVisible();

  // ---- 7. Оплата: платёж по API (UI платежей нет, S28) -------------------------
  // API-логин отзывает UI-сессию админа (одна активная сессия, S08),
  // поэтому платёж — после всех UI-шагов, а проверка — после повторного входа
  const adminToken = await apiLogin(ADMIN.email, ADMIN.password);
  const issued = (await listInvoices(adminToken)).find((i) => i.status === "ISSUED");
  if (!issued) throw new Error("Выпущенный счёт не найден в списке");
  await registerPayment(adminToken, issued.id, issued.totalAmount);

  await loginViaUi(adminPage, ADMIN.email, ADMIN.password);
  await adminPage.goto("/admin/documents");
  const paidRow = adminPage.locator("tr", { hasText: invoiceNumber }).first();
  await expect(paidRow).toBeVisible({ timeout: 15_000 });
  await expect(paidRow.getByText("Оплачен", { exact: true })).toBeVisible();

  await adminCtx.close();
  await clientCtx.close();
});
