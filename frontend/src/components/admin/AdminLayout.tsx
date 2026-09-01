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
            <NavLink to="/admin/users" className={({ isActive }) => (isActive ? styles.active : undefined)}>
              Пользователи
            </NavLink>
            <NavLink to="/admin/companies" className={({ isActive }) => (isActive ? styles.active : undefined)}>
              Компании
            </NavLink>
          </nav>
        </aside>

        <main className={styles.main}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
