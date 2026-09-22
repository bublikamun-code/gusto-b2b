import type { Role } from "../../types/auth";

export interface BackofficeNavItem {
  to: string;
  label: string;
}

// Единое меню бэк-офиса: один и тот же набор разделов на админке, складе
// и в CRM менеджера (матрица 2.1; бэкенд для чужих ролей отдаёт 403).
// Новый раздел бэк-офиса добавлять только сюда.
export function backofficeNavItems(role?: Role): BackofficeNavItem[] {
  if (role === "MANAGER") {
    return [
      { to: "/manager/inbox", label: "Единое окно" },
      { to: "/manager/orders", label: "Заказы" },
      { to: "/manager/leads", label: "Воронка лидов" },
      { to: "/manager/clients", label: "Клиенты" },
      { to: "/manager/dashboard", label: "CRM" },
      { to: "/manager/documents", label: "Документы" },
      { to: "/warehouse/balance", label: "Склад" },
    ];
  }
  return [
    { to: "/admin/dashboard", label: "Дашборд" },
    { to: "/admin/documents", label: "Документы" },
    { to: "/warehouse/balance", label: "Склад" },
    // CRM (S30): маршруты /manager/* открыты MANAGER и ADMIN
    { to: "/manager/inbox", label: "Единое окно" },
    { to: "/manager/orders", label: "Заказы" },
    { to: "/manager/dashboard", label: "CRM" },
    // Обмен 1С (S38): права как у импорта S35 — ADMIN/ACCOUNTANT
    ...(role === "ADMIN" || role === "ACCOUNTANT"
      ? [{ to: "/admin/integration", label: "Обмен 1С" }]
      : []),
    ...(role === "ADMIN"
      ? [
          { to: "/admin/users", label: "Пользователи" },
          { to: "/admin/companies", label: "Компании" },
          { to: "/admin/products", label: "Товары" },
          { to: "/admin/cms", label: "Страницы" },
          // Операционный центр (S38): настройки и аудит — только ADMIN
          { to: "/admin/settings", label: "Настройки" },
          { to: "/admin/audit", label: "Аудит" },
        ]
      : []),
  ];
}
