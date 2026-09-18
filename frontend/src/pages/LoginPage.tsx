import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useNavigate } from "react-router-dom";
import { Button, Card, Input, useToast } from "../components/ui";
import { login, resendEmailConfirmation } from "../api/auth";
import { putCartItem } from "../api/cart";
import { useAuthStore } from "../store/authStore";
import { useCartStore } from "../store/cartStore";
import { mergeLocalCartItems } from "../lib/cartMerge";
import styles from "./AuthPages.module.scss";

const loginSchema = z
  .object({
    email: z.string().email("Введите корректный email"),
    password: z.string().min(8, "Пароль не может быть короче 8 символов"),
    totpCode: z.string().optional(),
  })
  .refine((data) => !data.totpCode || /^\d{6}$/.test(data.totpCode), {
    message: "Код должен состоять из 6 цифр",
    path: ["totpCode"],
  });

type LoginForm = z.infer<typeof loginSchema>;

export default function LoginPage() {
  const [needsTotp, setNeedsTotp] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [emailNotConfirmed, setEmailNotConfirmed] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const { push } = useToast();
  const setAuth = useAuthStore((s) => s.setAuth);
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    setValue,
    getValues,
    formState: { errors },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "", totpCode: "" },
  });

  const onSubmit = async (values: LoginForm) => {
    setIsSubmitting(true);
    try {
      const payload = await login({
        email: values.email,
        password: values.password,
        totpCode: values.totpCode,
      });
      setAuth(payload.accessToken, payload.user);
      useCartStore.getState().setOwner(payload.user.id);
      // Перенос локальной корзины витрины в серверную (S21)
      const localItems = useCartStore.getState().items;
      if (localItems.length > 0) {
        for (const line of mergeLocalCartItems(localItems)) {
          await putCartItem(line.productId, line.quantity).catch(() => undefined);
        }
        useCartStore.getState().clear();
      }
      push("Вход выполнен", "success");

      const dashboardByRole: Record<string, string> = {
        ADMIN: "/admin",
        ACCOUNTANT: "/admin",
        MANAGER: "/manager",
        CUSTOMER_LEGAL: "/cabinet",
        CUSTOMER_INDIVIDUAL: "/cabinet",
      };
      navigate(dashboardByRole[payload.user.role] ?? "/cabinet", { replace: true });
    } catch (err) {
      const error = err as { code?: string; message?: string };
      if (error.code === "AUTH_2FA_REQUIRED" && !needsTotp) {
        setNeedsTotp(true);
        setValue("totpCode", "");
        push("Введите код из приложения аутентификации", "info");
      } else if (error.code === "AUTH_EMAIL_NOT_CONFIRMED") {
        setEmailNotConfirmed(true);
        push(error.message ?? "Email не подтверждён", "error");
      } else {
        push(error.message ?? "Не удалось войти", "error");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const onResendConfirmation = async () => {
    const email = getValues("email");
    if (!email) return;
    setIsResending(true);
    try {
      await resendEmailConfirmation(email);
      push("Если email существует и не подтверждён, письмо отправлено", "success");
    } catch (err) {
      const error = err as { message?: string };
      push(error.message ?? "Не удалось отправить письмо", "error");
    } finally {
      setIsResending(false);
    }
  };

  return (
    <div className={styles["auth-page"]}>
      <Card className={styles["auth-card"]}>
        <header className={styles["auth-header"]}>
          <span className={styles["auth-logo"]}>ГУСТО</span>
          <h1 className={styles["auth-title"]}>Вход в кабинет</h1>
        </header>

        <form className={styles["auth-form"]} onSubmit={handleSubmit(onSubmit)}>
          {!needsTotp ? (
            <>
              <Input
                label="Email"
                type="email"
                autoComplete="email"
                error={errors.email?.message}
                {...register("email")}
              />
              <Input
                label="Пароль"
                type="password"
                autoComplete="current-password"
                error={errors.password?.message}
                {...register("password")}
              />
            </>
          ) : (
            <>
              <p className={styles["auth-hint"]}>
                Для входа требуется код двухфакторной аутентификации
              </p>
              <Input
                label="Код из приложения"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                error={errors.totpCode?.message}
                {...register("totpCode")}
              />
            </>
          )}

          <div className={styles["auth-actions"]}>
            <Button type="submit" block loading={isSubmitting}>
              {needsTotp ? "Подтвердить" : "Войти"}
            </Button>
            {needsTotp && (
              <Button
                type="button"
                variant="secondary"
                block
                onClick={() => {
                  setNeedsTotp(false);
                  setValue("totpCode", "");
                }}
              >
                Назад
              </Button>
            )}
          </div>
        </form>

        {emailNotConfirmed && !needsTotp && (
          <div className={styles["auth-hint"]}>
            Email не подтверждён. Проверьте почту или запросите письмо повторно.
            <div className={styles["auth-actions"]}>
              <Button
                type="button"
                variant="secondary"
                block
                loading={isResending}
                onClick={onResendConfirmation}
              >
                Отправить письмо повторно
              </Button>
            </div>
          </div>
        )}

        <div className={styles["auth-links"]}>
          <Link to="/request-password-reset">Забыли пароль?</Link>
          <Link to="/register">Регистрация</Link>
        </div>
      </Card>
    </div>
  );
}
