import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Button } from "../../components/ui";
import { listCategories, listProducts } from "../../api/catalog";
import { ProductCard } from "../../components/public/ProductCard";
import styles from "./HomePage.module.scss";

const RUNNING_ITEMS = [
  "Яйца",
  "Фарш",
  "Колбаски",
  "Доставка за 2 часа",
  "Мясо",
  "Птица",
  "Яйца",
  "Фарш",
  "Колбаски",
  "Доставка за 2 часа",
  "Мясо",
  "Птица",
];

const HERO_STATS = [
  { value: "12", label: "ферм-партнёров" },
  { value: "2 ч", label: "доставка по городу" },
  { value: "0", label: "заморозки и рассолов" },
];

const DELIVERY_STEPS = [
  {
    number: "01",
    title: "Заказ до 14:00",
    text: "Принимаем заказы каждый день до 14:00. Доставка в тот же день — от фермы к вашему столу.",
  },
  {
    number: "02",
    title: "Режем и взвешиваем",
    text: "Готовим мясо и птицу под заказ: свежая нарезка, точный вес, фасовка в вакуум.",
  },
  {
    number: "03",
    title: "Привозим за 2 часа",
    text: "Доставляем по Минску и ближайшему пригороду в термо-рюкзаке. Мясо остаётся прохладным.",
  },
];

export default function HomePage() {
  const { data: categories = [] } = useQuery({
    queryKey: ["categories"],
    queryFn: listCategories,
  });

  const { data: hits } = useQuery({
    queryKey: ["products", "hits"],
    queryFn: () => listProducts({ page: 0, size: 6 }),
  });

  return (
    <>
      <section className={styles.hero}>
        <div className={styles.hero__content}>
          <span className={styles.hero__eyebrow}>Интернет-магазин · Минск</span>
          <h1 className={styles.hero__title}>
            Свежая поставка
            <br />
            каждое утро
          </h1>
          <p className={styles.hero__text}>
            Работаем с фермерскими хозяйствами Минской области напрямую, без посредников. Мясо
            охлаждённое, не заморозка. Привозим заказы за два часа — от фермы к вашему столу.
          </p>
          <div className={styles.hero__actions}>
            <Link to="/catalog">
              <Button variant="primary" size="lg">
                Смотреть каталог
              </Button>
            </Link>
            <Link to="/delivery">
              <Button variant="secondary" size="lg">
                Условия доставки
              </Button>
            </Link>
          </div>
          <div className={styles.hero__stats}>
            {HERO_STATS.map((stat) => (
              <div key={stat.label} className={styles.hero__stat}>
                <strong>{stat.value}</strong>
                <span>{stat.label}</span>
              </div>
            ))}
          </div>
        </div>
        <div className={styles.hero__visual}>
          <div className={styles.hero__image} role="img" aria-label="Свежий стейк на кости">
            <span className={styles.hero__priceTag}>
              <span>Стейк на кости</span>
              <strong>38,90 р./кг</strong>
            </span>
          </div>
        </div>
      </section>

      <div className={styles.runningLine}>
        <div className={styles.runningLine__track}>
          {RUNNING_ITEMS.map((item, index) => (
            <span key={index} className={styles.runningLine__item}>
              {item}
            </span>
          ))}
        </div>
      </div>

      <section className={styles.section}>
        <div className={styles.section__header}>
          <h2 className={styles.section__title}>Каталог</h2>
          <Link to="/catalog" className={styles.section__link}>
            Все товары →
          </Link>
        </div>
        <div className={styles.categories}>
          {categories.map((category) => (
            <Link
              key={category.id}
              to={`/catalog?categoryId=${category.id}`}
              className={styles.categoryCard}
            >
              <div className={styles.categoryCard__image} aria-hidden />
              <span className={styles.categoryCard__name}>{category.name}</span>
              <span className={styles.categoryCard__count}>смотреть</span>
            </Link>
          ))}
        </div>
      </section>

      <section className={[styles.section, styles.section_cream].join(" ")}>
        <div className={styles.section__header}>
          <h2 className={styles.section__title}>Хиты недели</h2>
          <Link to="/catalog" className={styles.section__link}>
            Перейти в каталог →
          </Link>
        </div>
        <div className={styles.productsGrid}>
          {hits?.items.map((product, index) => (
            <ProductCard key={product.id} product={product} badge={index < 3 ? "ХИТ" : "НОВИНКА"} />
          ))}
        </div>
      </section>

      <section className={styles.delivery}>
        <div className={styles.delivery__inner}>
          <h2 className={styles.delivery__title}>Как мы доставляем</h2>
          <div className={styles.delivery__steps}>
            {DELIVERY_STEPS.map((step) => (
              <div key={step.number} className={styles.delivery__step}>
                <span className={styles.delivery__number}>{step.number}</span>
                <h3>{step.title}</h3>
                <p>{step.text}</p>
              </div>
            ))}
          </div>
          <Link to="/delivery">
            <Button variant="accent" size="md">
              Подробнее о доставке
            </Button>
          </Link>
        </div>
      </section>
    </>
  );
}
