import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useNavigate } from "react-router-dom";
import { Button, Card, Input, useToast } from "../components/ui";
import { apiRequest } from "../api/client";
import { login } from "../api/auth";
import { useAuthStore } from "../store/authStore";
import { useCartStore } from "../store/cartStore";
import { mergeLocalCartItems } from "../lib/cartMerge";
import { putCartItem } from "../api/cart";
import styles from "./AuthPages.module.scss";

const registerSchema = z.object({
  fullName: z.string().min(2, "Укажите имя и фамилию"),
  email: z.string().email("Введите корректный email"),
  phone: z.string().min(7, "Укажите телефон"),
  password: z.string().min(8, "Пароль не может быть короче 8 символов"),
});

type RegisterForm = z.infer<typeof registerSchema>;

/** Короткая саморегистрация физлица (1.6, S21): email/телефон + пароль. */
export default function RegisterPage() {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { push } = useToast();
  const setAuth = useAuthStore((s) => s.setAuth);
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: "", email: "", phone: "", password: "" },
  });

  const onSubmit = async (values: RegisterForm) => {
    setIsSubmitting(true);
    try {
      await apiRequest("/auth/register", { method: "POST", body: values });
      // Гейт подтверждения email по умолчанию выключен — сразу входим
      const payload = await login({ email: values.email, password: values.password });
      setAuth(payload.accessToken, payload.user);
      useCartStore.getState().setOwner(payload.user.id);
      await migrateLocalCart();
      push("Добро пожаловать в Густо!", "success");
      navigate("/cabinet/cart", { replace: true });
    } catch (err) {
      const error = err as { message?: string };
      push(error.message ?? "Не удалось зарегистрироваться", "error");
    } finally {
      setIsSubmitting(false);
    }
  };

  /** Локальная корзина витрины переезжает в серверную (S21). */
  async function migrateLocalCart() {
    const local = useCartStore.getState().items;
    for (const line of mergeLocalCartItems(local)) {
      await putCartItem(line.productId, line.quantity).catch(() => undefined);
    }
    useCartStore.getState().clear();
  }

  return (
    <div className={styles["auth-page"]}>
      <Card className={styles["auth-card"]}>
        <header className={styles["auth-header"]}>
          <span className={styles["auth-logo"]}>ГУСТО</span>
          <h1 className={styles["auth-title"]}>Регистрация</h1>
        </header>

        <form className={styles["auth-form"]} onSubmit={handleSubmit(onSubmit)}>
          <Input label="Имя и фамилия" error={errors.fullName?.message} {...register("fullName")} />
          <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register("email")} />
          <Input label="Телефон" autoComplete="tel" placeholder="+375 29 …" error={errors.phone?.message} {...register("phone")} />
          <Input
            label="Пароль"
            type="password"
            autoComplete="new-password"
            error={errors.password?.message}
            {...register("password")}
          />
          <div className={styles["auth-actions"]}>
            <Button type="submit" block loading={isSubmitting}>
              Зарегистрироваться
            </Button>
          </div>
        </form>

        <div className={styles["auth-links"]}>
          <Link to="/login">Уже есть аккаунт? Войти</Link>
        </div>
      </Card>
    </div>
  );
}
