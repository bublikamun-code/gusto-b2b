import { NavLink, Outlet } from "react-router-dom";
import { BackofficeMenuButton, BackofficeMenuPanel, useBackofficeMenu } from "../admin/BackofficeMenu";
import styles from "./WarehouseLayout.module.scss";

const NAV_ITEMS = [
  { to: "/warehouse/balance", label: "Остатки" },
  { to: "/warehouse/documents", label: "Документы" },
  { to: "/warehouse/suppliers", label: "Поставщики" },
  { to: "/warehouse/purchase-orders", label: "Заказы поставщикам" },
  { to: "/warehouse/reports", label: "Отчёты" },
];

export function WarehouseLayout() {
  const menu = useBackofficeMenu();

  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <BackofficeMenuButton open={menu.open} onToggle={menu.toggle} />
        <span className={styles.title}>Склад</span>
        <nav className={styles.nav}>
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => (isActive ? styles.active : undefined)}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <div className={styles.body}>
        <BackofficeMenuPanel open={menu.open} />
        <main className={styles.main}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
