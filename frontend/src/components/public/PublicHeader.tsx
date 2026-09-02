import { Link, useLocation } from "react-router-dom";
import { Button } from "../ui";
import styles from "./PublicHeader.module.scss";

const NAV_LINKS = [
  { to: "/", label: "Главная" },
  { to: "/catalog", label: "Каталог" },
  { to: "/delivery", label: "Доставка" },
  { to: "/about", label: "О нас" },
  { to: "/contacts", label: "Контакты" },
];

export function PublicHeader() {
  const location = useLocation();

  return (
    <header className={styles.header}>
      <Link to="/" className={styles.logo}>
        <span className={styles.logo__mark} aria-hidden>
          <span className={styles.logo__icon} />
        </span>
        <span className={styles.logo__text}>
          <strong>ГУСТО</strong>
          <span>мясной гастроном</span>
        </span>
      </Link>

      <nav className={styles.nav}>
        {NAV_LINKS.map((link) => (
          <Link
            key={link.to}
            to={link.to}
            className={[styles.nav__link, location.pathname === link.to ? styles.nav__link_active : ""]
              .filter(Boolean)
              .join(" ")}
          >
            {link.label}
          </Link>
        ))}
      </nav>

      <div className={styles.actions}>
        <Link to="/login" className={styles.actions__login}>
          Войти
        </Link>
        <Button variant="primary" size="sm">
          Корзина
        </Button>
      </div>
    </header>
  );
}
