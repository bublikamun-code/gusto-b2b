import { Button } from "../../components/ui";
import { logout } from "../../api/auth";
import { useAuthStore } from "../../store/authStore";

export default function AdminDashboardPage() {
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
        <span className="header__sub">Администратор</span>
      </header>
      <main className="card">
        <h1>Админ-панель</h1>
        <p>Пользователь: {user?.fullName ?? user?.email}</p>
        <Button onClick={handleLogout}>Выйти</Button>
      </main>
    </div>
  );
}
