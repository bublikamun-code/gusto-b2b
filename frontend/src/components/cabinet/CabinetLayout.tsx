import { useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "../ui";
import { useAuthStore } from "../../store/authStore";
import { useCartStore } from "../../store/cartStore";
import { logout } from "../../api/auth";
import { getCart } from "../../api/cart";
import styles from "./CabinetLayout.module.scss";

export function CabinetLayout() {
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [loggingOut, setLoggingOut] = useState(false);

  // Счётчик корзины — тот же кэш ["cart"], что и у страниц кабинета: лишний запрос не создаётся
  const { data: cart } = useQuery({ queryKey: ["cart"], queryFn: getCart });
  const cartCount = cart?.items.reduce((acc, item) => acc + item.quantity, 0) ?? 0;

  // Документы — только юрлицам (матрица 2.1); физлицо пункт не видит
  const navItems = [
    { to: "/cabinet/catalog", label: "Каталог" },
    { to: "/cabinet/cart", label: `Корзина${cartCount > 0 ? ` (${cartCount})` : ""}` },
    { to: "/cabinet/orders", label: "Заказы" },
    ...(user?.role === "CUSTOMER_LEGAL" ? [{ to: "/cabinet/documents", label: "Документы" }] : []),
    { to: "/cabinet/profile", label: "Профиль" },
  ];

  const handleLogout = () => {
    setLoggingOut(true);
    logout().finally(() => {
      useCartStore.getState().clear();
      useCartStore.getState().setOwner(null);
      queryClient.removeQueries({ queryKey: ["cart"] });
      clearAuth();
      navigate("/login", { replace: true });
    });
  };

  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <Link to="/cabinet" className={styles.logo}>
          <span className={styles.logo__text}>ГУСТО</span>
          <span className={styles.logo__sub}>кабинет клиента</span>
        </Link>

        <nav className={styles.nav}>
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                isActive ? `${styles.nav__link} ${styles.nav__link_active}` : styles.nav__link
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className={styles.actions}>
          <Link to="/" className={styles.actions__storefront}>
            Витрина
          </Link>
          <span className={styles.actions__user}>{user?.fullName ?? user?.email}</span>
          <Button
            variant="secondary"
            size="sm"
            className={styles.logout}
            loading={loggingOut}
            onClick={handleLogout}
          >
            Выйти
          </Button>
        </div>
      </header>

      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}
