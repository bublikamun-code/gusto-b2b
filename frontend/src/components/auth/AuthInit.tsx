import { useEffect, type ReactNode } from "react";
import { Outlet } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { refresh, me } from "../../api/auth";

interface AuthInitProps {
  children?: ReactNode;
}

/**
 * AuthInit выполняется один раз при старте приложения:
 * пробуем silent-refresh по httpOnly-куке, затем /auth/me по accessToken.
 * После попытки рендерим детей / Outlet.
 */
export function AuthInit({ children }: AuthInitProps) {
  const { accessToken, setAuth, clearAuth, setLoading } = useAuthStore();

  useEffect(() => {
    let cancelled = false;

    async function init() {
      try {
        let token = accessToken;
        if (!token) {
          const refreshed = await refresh();
          token = refreshed.accessToken;
        }
        const user = await me();
        if (!cancelled) {
          setAuth(token, user);
        }
      } catch {
        if (!cancelled) {
          clearAuth();
          setLoading(false);
        }
      }
    }

    void init();

    return () => {
      cancelled = true;
    };
  }, [accessToken, setAuth, clearAuth, setLoading]);

  return children ? <>{children}</> : <Outlet />;
}
