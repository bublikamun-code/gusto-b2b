import { useState } from "react";
import { Button, LinkButton } from "../../components/ui";
import { logout } from "../../api/auth";
import { useAuthStore } from "../../store/authStore";

export default function ManagerDashboardPage() {
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [loggingOut, setLoggingOut] = useState(false);

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      setLoggingOut(false);
      clearAuth();
      window.location.href = "/login";
    }
  };

  return (
    <div className="page">
      <header className="header">
        <span className="logo">ГУСТО</span>
        <span className="header__sub">Менеджер</span>
      </header>
      <main className="card">
        <h1>Кабинет менеджера</h1>
        <p>Пользователь: {user?.fullName ?? user?.email}</p>
        <div className="actions">
          <LinkButton to="/manager/inbox">Единое окно</LinkButton>
          <LinkButton to="/manager/leads">Лиды</LinkButton>
          <LinkButton to="/manager/clients">Клиенты</LinkButton>
          <LinkButton to="/manager/dashboard">Дашборд</LinkButton>
          <LinkButton to="/manager/orders">Заказы</LinkButton>
          <LinkButton to="/manager/orders/new" variant="accent">
            Заказ от имени клиента
          </LinkButton>
          <LinkButton to="/manager/documents">Документы</LinkButton>
          <Button variant="secondary" loading={loggingOut} onClick={handleLogout}>
            Выйти
          </Button>
        </div>
      </main>
    </div>
  );
}
