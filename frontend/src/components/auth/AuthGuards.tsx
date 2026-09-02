import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { DASHBOARD_BY_ROLE } from "./dashboardByRole";
import type { Role } from "../../types/admin";

export function ProtectedRoute() {
  const { user, isLoading } = useAuthStore();
  const location = useLocation();

  if (isLoading) {
    return <div className="auth-loading">Загрузка…</div>;
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <Outlet />;
}

export function RoleGuard({ allowed }: { allowed: Role[] }) {
  const { user, isLoading } = useAuthStore();
  const location = useLocation();

  if (isLoading) {
    return <div className="auth-loading">Загрузка…</div>;
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (!allowed.includes(user.role)) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}

export function AuthRedirect({ children }: { children: React.ReactNode }) {
  const { user, isLoading } = useAuthStore();

  if (isLoading) {
    return <div className="auth-loading">Загрузка…</div>;
  }

  if (user) {
    const target = DASHBOARD_BY_ROLE[user.role] ?? "/cabinet";
    return <Navigate to={target} replace />;
  }

  return children;
}

// Индекс админки: ADMIN попадает в пользователей, остальные роли — на дашборд
// (разделы пользователей/компаний/товаров закрыты постраничным RoleGuard'ом, см. App).
export function AdminIndexRedirect() {
  const { user, isLoading } = useAuthStore();

  if (isLoading) {
    return <div className="auth-loading">Загрузка…</div>;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return <Navigate to={user.role === "ADMIN" ? "/admin/users" : "/admin/dashboard"} replace />;
}
