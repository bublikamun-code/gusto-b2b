import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { logout } from "../../api/auth";
import { Button } from "../ui";
import styles from "./AdminLayout.module.scss";

export function AdminLayout() {
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await logout();
    } finally {
      clearAuth();
      navigate("/login", { replace: true });
    }
  };

  // Навигация по матрице 2.1: дашборд доступен и бухгалтеру,
  // CRUD-разделы — только ADMIN (бэкенд /admin/** для остальных отдаёт 403).
  const navItems = [
    { to: "/admin/dashboard", label: "Дашборд" },
    { to: "/admin/documents", label: "Документы" },
    // Склад (S18.4): ADMIN/ACCOUNTANT/MANAGER по матрице 2.1
    { to: "/warehouse/balance", label: "Склад" },
    // Обмен 1С (S38): права как у импорта S35 — ADMIN/ACCOUNTANT
    ...(user?.role === "ADMIN" || user?.role === "ACCOUNTANT"
      ? [{ to: "/admin/integration", label: "Обмен 1С" }]
      : []),
    ...(user?.role === "ADMIN"
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

  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <Link to="/admin" className={styles.logo}>
          ГУСТО
        </Link>
        <div className={styles.headerRight}>
          <span className={styles.user}>{user?.fullName ?? user?.email}</span>
          <Button variant="secondary" size="sm" onClick={handleLogout}>
            Выйти
          </Button>
        </div>
      </header>

      <div className={styles.body}>
        <aside className={styles.sidebar}>
          <nav className={styles.nav}>
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) => (isActive ? styles.active : undefined)}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </aside>

        <main className={styles.main}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
