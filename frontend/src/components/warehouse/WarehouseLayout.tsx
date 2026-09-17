import { NavLink, Outlet } from "react-router-dom";
import styles from "./WarehouseLayout.module.scss";

const NAV_ITEMS = [
  { to: "/warehouse/balance", label: "Остатки" },
  { to: "/warehouse/documents", label: "Документы" },
  { to: "/warehouse/suppliers", label: "Поставщики" },
  { to: "/warehouse/purchase-orders", label: "Заказы поставщикам" },
  { to: "/warehouse/reports", label: "Отчёты" },
];

export function WarehouseLayout() {
  return (
    <div className={styles.layout}>
      <header className={styles.header}>
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
      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}
