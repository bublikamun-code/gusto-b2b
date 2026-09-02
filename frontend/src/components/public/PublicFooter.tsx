import { Link } from "react-router-dom";
import styles from "./PublicFooter.module.scss";

const NAV_LINKS = [
  { to: "/", label: "Главная" },
  { to: "/catalog", label: "Каталог" },
  { to: "/delivery", label: "Доставка" },
  { to: "/about", label: "О нас" },
  { to: "/contacts", label: "Контакты" },
];

const LEGAL_LINKS = [{ to: "/privacy", label: "Политика конфиденциальности" }];

export function PublicFooter() {
  return (
    <footer className={styles.footer}>
      <div className={styles.footer__grid}>
        <div className={styles.footer__brand}>
          <Link to="/" className={styles.logo}>
            <span className={styles.logo__mark} aria-hidden>
              <span className={styles.logo__icon} />
            </span>
            <strong className={styles.logo__text}>ГУСТО</strong>
          </Link>
          <p className={styles.footer__desc}>
            Мясной гастроном с доставкой. Мясо, птица и яйца от фермеров Минской области.
          </p>
        </div>

        <div className={styles.footer__nav}>
          <h4 className={styles.footer__title}>Навигация</h4>
          <ul>
            {NAV_LINKS.map((link) => (
              <li key={link.to}>
                <Link to={link.to}>{link.label}</Link>
              </li>
            ))}
          </ul>
        </div>

        <div className={styles.footer__contacts}>
          <h4 className={styles.footer__title}>Контакты</h4>
          <p>+375 29 123-45-67</p>
          <p>info@gustomeat.by</p>
          <p>Ежедневно 9:00–21:00</p>
          <p>Минск, доставка по городу</p>
        </div>
      </div>

      <div className={styles.footer__bottom}>
        <span>© 2026 ГУСТО</span>
        <span>
          {LEGAL_LINKS.map((link) => (
            <Link key={link.to} to={link.to}>
              {link.label}
            </Link>
          ))}
        </span>
        <span>gustomeat.by</span>
      </div>
    </footer>
  );
}
