import { useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { logout } from "../../api/auth";
import { Button } from "../ui";
import { BackofficeMenuButton, BackofficeMenuPanel, useBackofficeMenu } from "../admin/BackofficeMenu";
// Шапка и сетка — те же, что в админке: единый каркас бэк-офиса,
// выпадающее меню выглядит одинаково во всех разделах.
import adminStyles from "../admin/AdminLayout.module.scss";
import styles from "./WarehouseLayout.module.scss";

const NAV_ITEMS = [
  { to: "/warehouse/balance", label: "Остатки" },
  { to: "/warehouse/documents", label: "Документы" },
  { to: "/warehouse/suppliers", label: "Поставщики" },
  { to: "/warehouse/purchase-orders", label: "Заказы поставщикам" },
  { to: "/warehouse/reports", label: "Отчёты" },
];

export function WarehouseLayout() {
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);
  const menu = useBackofficeMenu();

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      setLoggingOut(false);
      clearAuth();
      navigate("/login", { replace: true });
    }
  };

  return (
    <div className={adminStyles.layout}>
      <header className={adminStyles.header}>
        <div className={adminStyles.headerStart}>
          <BackofficeMenuButton open={menu.open} onToggle={menu.toggle} />
          <Link to="/warehouse/balance" className={adminStyles.logo}>
            ГУСТО
          </Link>
          <span className={styles.title}>Склад</span>
        </div>
        <div className={adminStyles.headerRight}>
          <span className={adminStyles.user}>{user?.fullName ?? user?.email}</span>
          <Button variant="secondary" size="sm" loading={loggingOut} onClick={handleLogout}>
            Выйти
          </Button>
        </div>
      </header>

      <nav className={styles.subnav} aria-label="Разделы склада">
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

      <div className={adminStyles.body}>
        <BackofficeMenuPanel open={menu.open} />
        <main className={adminStyles.main}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
