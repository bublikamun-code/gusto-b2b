import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link } from "react-router-dom";
import { Button, Card, Input, useToast } from "../components/ui";
import { requestPasswordReset } from "../api/auth";
import styles from "./AuthPages.module.scss";

const schema = z.object({
  email: z.string().email("Введите корректный email"),
});

type FormValues = z.infer<typeof schema>;

export default function RequestPasswordResetPage() {
  const [isSent, setIsSent] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { push } = useToast();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: "" },
  });

  const onSubmit = async (values: FormValues) => {
    setIsSubmitting(true);
    try {
      await requestPasswordReset(values);
      setIsSent(true);
      push("Если email зарегистрирован, ссылка отправлена", "success");
    } catch (err) {
      const error = err as { message?: string };
      push(error.message ?? "Не удалось отправить запрос", "error");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className={styles["auth-page"]}>
      <Card className={styles["auth-card"]}>
        <header className={styles["auth-header"]}>
          <span className={styles["auth-logo"]}>ГУСТО</span>
          <h1 className={styles["auth-title"]}>Восстановление пароля</h1>
        </header>

        {isSent ? (
          <p className={styles["auth-hint"]}>
            Проверьте почту. Если указанный email зарегистрирован, мы отправили ссылку
            для сброса пароля.
          </p>
        ) : (
          <form className={styles["auth-form"]} onSubmit={handleSubmit(onSubmit)}>
            <Input
              label="Email"
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              {...register("email")}
            />
            <div className={styles["auth-actions"]}>
              <Button type="submit" block loading={isSubmitting}>
                Отправить ссылку
              </Button>
            </div>
          </form>
        )}

        <div className={styles["auth-links"]}>
          <Link to="/login">Вернуться ко входу</Link>
        </div>
      </Card>
    </div>
  );
}
