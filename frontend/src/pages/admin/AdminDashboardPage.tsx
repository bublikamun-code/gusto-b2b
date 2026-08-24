import { useAuthStore } from "../../store/authStore";
import styles from "./AdminPages.module.scss";

export default function AdminDashboardPage() {
  const user = useAuthStore((s) => s.user);

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Админ-панель</h1>
      <p>Добро пожаловать, {user?.fullName ?? user?.email}.</p>
      <p>Выберите раздел в боковом меню: Пользователи или Компании.</p>
    </div>
  );
}
