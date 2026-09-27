import { LinkButton } from "../components/ui";
import { useAuthStore } from "../store/authStore";
import { DASHBOARD_BY_ROLE } from "../components/auth/dashboardByRole";
import styles from "./NotFoundPage.module.scss";

/** Роль не даёт доступ к разделу: раньше был тихий редирект на главную (S44). */
export default function ForbiddenPage() {
  const user = useAuthStore((s) => s.user);
  const home = user ? DASHBOARD_BY_ROLE[user.role] ?? "/cabinet" : "/";

  return (
    <div className="page">
      <main className={styles.wrapper}>
        <p className={styles.code}>403</p>
        <h1 className={styles.title}>Недостаточно прав</h1>
        <p>Этот раздел недоступен вашей роли. Если нужно больше прав — обратитесь к администратору.</p>
        <LinkButton to={home}>На главную</LinkButton>
      </main>
    </div>
  );
}
