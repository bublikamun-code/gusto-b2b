import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Card, useToast } from "../components/ui";
import { confirmEmail } from "../api/auth";
import styles from "./AuthPages.module.scss";

type PageState = "confirming" | "confirmed" | "invalid";

export default function ConfirmEmailPage() {
  const [searchParams] = useSearchParams();
  const [state, setState] = useState<PageState>("confirming");
  const { push } = useToast();
  const token = searchParams.get("token");

  useEffect(() => {
    if (!token) {
      setState("invalid");
      return;
    }
    let cancelled = false;
    confirmEmail(token)
      .then(() => {
        if (!cancelled) setState("confirmed");
      })
      .catch(() => {
        if (!cancelled) setState("invalid");
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    if (state === "confirmed") push("Email подтверждён", "success");
  }, [state, push]);

  return (
    <div className={styles["auth-page"]}>
      <Card className={styles["auth-card"]}>
        <header className={styles["auth-header"]}>
          <span className={styles["auth-logo"]}>ГУСТО</span>
          <h1 className={styles["auth-title"]}>Подтверждение email</h1>
        </header>

        {state === "confirming" && (
          <p className={styles["auth-hint"]}>Проверяем ссылку…</p>
        )}

        {state === "confirmed" && (
          <p className={styles["auth-hint"]}>
            Email подтверждён. Теперь вы можете войти в кабинет.
          </p>
        )}

        {state === "invalid" && (
          <p className={styles["auth-hint"]}>
            Ссылка недействительна или истекла. Запросите новое письмо на странице входа:
            при попытке входа выберите «Отправить письмо повторно».
          </p>
        )}

        <div className={styles["auth-links"]}>
          <Link to="/login">
            {state === "confirmed" ? "Перейти ко входу" : "На страницу входа"}
          </Link>
        </div>
      </Card>
    </div>
  );
}
