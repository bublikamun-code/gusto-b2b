import { useAuthStore } from "../../store/authStore";
import styles from "./AdminPages.module.scss";

export default function AdminDashboardPage() {
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === "ADMIN";

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>{isAdmin ? "Админ-панель" : "Кабинет бухгалтера"}</h1>
      <p>Добро пожаловать, {user?.fullName ?? user?.email}.</p>
      <p>
        {isAdmin
          ? "Выберите раздел в боковом меню: Пользователи, Компании или Товары."
          : "Разделы склада и документов в разработке — следите за обновлениями."}
      </p>
    </div>
  );
}
