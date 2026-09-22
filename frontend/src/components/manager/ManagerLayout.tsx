import { useState } from "react";
import { Link, Outlet, useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { logout } from "../../api/auth";
import { Button } from "../ui";
import { BackofficeMenuButton, BackofficeMenuPanel, useBackofficeMenu } from "../admin/BackofficeMenu";
// Шапка и сетка — те же, что в админке: единый каркас бэк-офиса
import adminStyles from "../admin/AdminLayout.module.scss";

export function ManagerLayout() {
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
          <Link to="/manager/dashboard" className={adminStyles.logo}>
            ГУСТО
          </Link>
        </div>
        <div className={adminStyles.headerRight}>
          <span className={adminStyles.user}>{user?.fullName ?? user?.email}</span>
          <Button variant="secondary" size="sm" loading={loggingOut} onClick={handleLogout}>
            Выйти
          </Button>
        </div>
      </header>

      <div className={adminStyles.body}>
        <BackofficeMenuPanel open={menu.open} />
        <main className={adminStyles.main}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
