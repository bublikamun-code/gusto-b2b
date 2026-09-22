import { useState } from "react";
import { Link, Outlet, useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { logout } from "../../api/auth";
import { Button } from "../ui";
import { BackofficeMenuButton, BackofficeMenuPanel, useBackofficeMenu } from "./BackofficeMenu";
import styles from "./AdminLayout.module.scss";

export function AdminLayout() {
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
    <div className={styles.layout}>
      <header className={styles.header}>
        <div className={styles.headerStart}>
          <BackofficeMenuButton open={menu.open} onToggle={menu.toggle} />
          <Link to="/admin" className={styles.logo}>
            ГУСТО
          </Link>
        </div>
        <div className={styles.headerRight}>
          <span className={styles.user}>{user?.fullName ?? user?.email}</span>
          <Button variant="secondary" size="sm" loading={loggingOut} onClick={handleLogout}>
            Выйти
          </Button>
        </div>
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
