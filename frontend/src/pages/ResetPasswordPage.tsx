import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, Input, useToast } from "../components/ui";
import { confirmPasswordReset } from "../api/auth";
import styles from "./AuthPages.module.scss";

const schema = z
  .object({
    newPassword: z.string().min(8, "Пароль не может быть короче 8 символов"),
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Пароли не совпадают",
    path: ["confirmPassword"],
  });

type FormValues = z.infer<typeof schema>;

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isDone, setIsDone] = useState(false);
  const { push } = useToast();
  const navigate = useNavigate();
  const token = searchParams.get("token");

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  });

  useEffect(() => {
    if (!token) {
      push("Ссылка для сброса пароля некорректна", "error");
    }
  }, [token, push]);

  const onSubmit = async (values: FormValues) => {
    if (!token) return;
    setIsSubmitting(true);
    try {
      await confirmPasswordReset({ token, newPassword: values.newPassword });
      setIsDone(true);
      push("Пароль изменён", "success");
      setTimeout(() => navigate("/login", { replace: true }), 2000);
    } catch (err) {
      const error = err as { message?: string };
      push(error.message ?? "Не удалось сменить пароль", "error");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className={styles["auth-page"]}>
      <Card className={styles["auth-card"]}>
        <header className={styles["auth-header"]}>
          <span className={styles["auth-logo"]}>ГУСТО</span>
          <h1 className={styles["auth-title"]}>Новый пароль</h1>
        </header>

        {isDone ? (
          <p className={styles["auth-hint"]}>
            Пароль изменён. Сейчас вы будете перенаправлены на страницу входа.
          </p>
        ) : (
          <form className={styles["auth-form"]} onSubmit={handleSubmit(onSubmit)}>
            <Input
              label="Новый пароль"
              type="password"
              autoComplete="new-password"
              error={errors.newPassword?.message}
              {...register("newPassword")}
            />
            <Input
              label="Повторите пароль"
              type="password"
              autoComplete="new-password"
              error={errors.confirmPassword?.message}
              {...register("confirmPassword")}
            />
            <div className={styles["auth-actions"]}>
              <Button type="submit" block loading={isSubmitting} disabled={!token}>
                Сохранить пароль
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
