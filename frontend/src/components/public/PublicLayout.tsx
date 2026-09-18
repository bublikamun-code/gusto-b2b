import { Outlet, useLocation } from "react-router-dom";
import { useEffect } from "react";
import { PublicHeader } from "./PublicHeader";
import { PublicFooter } from "./PublicFooter";
import styles from "./PublicLayout.module.scss";

const PAGE_META: Record<string, { title: string; description: string }> = {
  "/": {
    title: "ГУСТО — мясной гастроном | Свежая поставка каждое утро",
    description:
      "Фермерское мясо и птица охлаждённые, без заморозки. Доставка по Минску за 2 часа. Розничные цены онлайн.",
  },
  "/catalog": {
    title: "Каталог — ГУСТО мясной гастроном",
    description:
      "Стейки, фарш, птица, яйца и колбаски с розничными ценами. Заказ онлайн, доставка за 2 часа.",
  },
  "/delivery": {
    title: "Доставка — ГУСТО мясной гастроном",
    description: "Доставляем по Минску в термо-рюкзаке за 2 часа. Заказы принимаем ежедневно до 14:00.",
  },
  "/about": {
    title: "О нас — ГУСТО мясной гастроном",
    description: "Работаем с фермерскими хозяйствами Минской области напрямую.",
  },
  "/contacts": {
    title: "Контакты — ГУСТО мясной гастроном",
    description: "Телефон, email и реквизиты для связи и сотрудничества.",
  },
  "/privacy": {
    title: "Политика конфиденциальности — ГУСТО",
    description: "Как мы обрабатываем персональные данные клиентов.",
  },
};

export function PublicLayout() {
  const { pathname } = useLocation();

  // Базовое SEO витрины (S21): title + description на публичных страницах
  useEffect(() => {
    const meta = PAGE_META[pathname];
    if (meta) {
      document.title = meta.title;
      let desc = document.head.querySelector<HTMLMetaElement>('meta[name="description"]');
      if (!desc) {
        desc = document.createElement("meta");
        desc.setAttribute("name", "description");
        document.head.appendChild(desc);
      }
      desc.setAttribute("content", meta.description);
    }
  }, [pathname]);

  return (
    <div className={styles.layout}>
      <PublicHeader />
      <main className={styles.main}>
        <Outlet />
      </main>
      <PublicFooter />
    </div>
  );
}
