import { Button } from "../components/ui";
import styles from "./NotFoundPage.module.scss";

export default function NotFoundPage() {
  return (
    <div className="page">
      <main className={styles.wrapper}>
        <p className={styles.code}>404</p>
        <h1 className={styles.title}>Страница не найдена</h1>
        <Button onClick={() => (window.location.href = "/")}>На главную</Button>
      </main>
    </div>
  );
}
