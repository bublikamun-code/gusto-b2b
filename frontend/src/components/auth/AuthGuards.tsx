import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";

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

const DASHBOARD_BY_ROLE: Record<string, string> = {
  ADMIN: "/admin",
  ACCOUNTANT: "/admin",
  MANAGER: "/manager",
  CUSTOMER_LEGAL: "/cabinet",
  CUSTOMER_INDIVIDUAL: "/cabinet",
};

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
