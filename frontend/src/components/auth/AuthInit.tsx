import { useEffect, type ReactNode } from "react";
import { Outlet } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { useCartStore } from "../../store/cartStore";
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
  const setAuth = useAuthStore((s) => s.setAuth);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const setLoading = useAuthStore((s) => s.setLoading);

  useEffect(() => {
    let cancelled = false;

    async function init() {
      try {
        let token = useAuthStore.getState().accessToken;
        if (!token) {
          const refreshed = await refresh();
          token = refreshed.accessToken;
          // me() читает токен из стора — сохраняем до вызова
          if (!cancelled) {
            useAuthStore.setState({ accessToken: token });
          }
        }
        const user = await me();
        if (!cancelled) {
          setAuth(token, user);
          useCartStore.getState().setOwner(user.id);
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
    // Экшены zustand стабильны: эффект выполнится один раз при старте приложения
  }, [setAuth, clearAuth, setLoading]);

  return children ? <>{children}</> : <Outlet />;
}
