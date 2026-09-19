import { Link } from "react-router-dom";
import { Button } from "../../components/ui";
import { logout } from "../../api/auth";
import { useAuthStore } from "../../store/authStore";

export default function ManagerDashboardPage() {
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);

  const handleLogout = async () => {
    try {
      await logout();
    } finally {
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
          <Link to="/manager/orders">
            <Button>Заказы</Button>
          </Link>
          <Link to="/manager/orders/new">
            <Button variant="accent">Заказ от имени клиента</Button>
          </Link>
          <Link to="/manager/documents">
            <Button>Документы</Button>
          </Link>
          <Button variant="secondary" onClick={handleLogout}>
            Выйти
          </Button>
        </div>
      </main>
    </div>
  );
}
