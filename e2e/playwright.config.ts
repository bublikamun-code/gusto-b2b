import { defineConfig, devices } from "@playwright/test";

/**
 * E2E полного цикла (S39). Стек поднимается отдельно: `make up`
 * (backend :8080, frontend :5173). Перед запуском: `npm ci` + `npx playwright install chromium`.
 */
export default defineConfig({
  testDir: "./tests",
  timeout: 90_000,
  expect: { timeout: 10_000 },
  fullyParallel: false, // тесты делят одну БД стека — гоняем последовательно
  workers: 1,
  retries: 0,
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]],
  use: {
    baseURL: process.env.E2E_FRONT_URL ?? "http://localhost:5173",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    // Полный цикл — десктоп
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } },
    },
    // Витрина — мобильный вьюпорт 375px (розница приходит с телефона)
    {
      name: "mobile-chrome",
      testMatch: /showcase\.spec\.ts/,
      use: {
        ...devices["Pixel 7"],
        viewport: { width: 375, height: 812 },
      },
    },
  ],
});
