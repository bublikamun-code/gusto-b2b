import { useState } from "react";
import { NavLink } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { backofficeNavItems } from "./adminNav";
import styles from "./BackofficeMenu.module.scss";

// Единое меню бэк-офиса (админка / склад / CRM): кнопка «Меню» в шапке
// разворачивает одну и ту же панель разделов по роли.

export function useBackofficeMenu() {
  const [open, setOpen] = useState(false);
  return { open, toggle: () => setOpen((value) => !value) };
}

export function BackofficeMenuButton({ open, onToggle }: { open: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      className={styles.toggle}
      aria-expanded={open}
      aria-controls="backoffice-nav"
      onClick={onToggle}
    >
      Меню
    </button>
  );
}

export function BackofficeMenuPanel({ open }: { open: boolean }) {
  const user = useAuthStore((s) => s.user);
  if (!open) return null;
  return (
    <aside className={styles.panel}>
      <nav id="backoffice-nav" className={styles.nav} aria-label="Бэк-офис">
        {backofficeNavItems(user?.role).map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => (isActive ? styles.active : undefined)}
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
