import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { LinkButton } from "../../components/ui";
import { listCategories, listProducts } from "../../api/catalog";
import { getPublicLanding } from "../../api/adminOperations";
import { resolveLanding, type PublicLanding } from "../../lib/landingTexts";
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

export default function HomePage() {
  const { data: categories = [], isError: categoriesError } = useQuery({
    queryKey: ["categories"],
    queryFn: listCategories,
  });

  // «Хиты недели» — по флагу isHit из админки (S19.1), а не «первые 6»
  const { data: hits, isError: hitsError } = useQuery({
    queryKey: ["products", "hits"],
    queryFn: async () => {
      const page = await listProducts({ page: 0, size: 50 });
      return { ...page, items: page.items.filter((product) => product.isHit).slice(0, 6) };
    },
  });

  // Тексты лендинга из настроек (S38); пустые значения — дефолты витрины
  const { data: landing } = useQuery({
    queryKey: ["landing-texts"],
    queryFn: getPublicLanding,
    staleTime: 60_000,
  });
  const { hero, deliveryTitle, deliverySteps } = resolveLanding(
    landing as PublicLanding | undefined,
  );

  return (
    <>
      <section className={styles.hero}>
        <div className={styles.hero__content}>
          <span className={styles.hero__eyebrow}>{hero.eyebrow}</span>
          <h1 className={styles.hero__title}>{hero.title}</h1>
          <p className={styles.hero__text}>{hero.text}</p>
          <div className={styles.hero__actions}>
            <LinkButton to="/catalog" variant="primary" size="lg">
              Смотреть каталог
            </LinkButton>
            <LinkButton to="/delivery" variant="secondary" size="lg">
              Условия доставки
            </LinkButton>
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
            <span className={styles.hero__mark}>
              <span className={styles.hero__markIcon} />
            </span>
            <span className={styles.hero__wordmark}>ГУСТО</span>
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
        {categoriesError && <p className={styles.section__error}>Не удалось загрузить категории.</p>}
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
        {hitsError && <p className={styles.section__error}>Не удалось загрузить товары.</p>}
        <div className={styles.productsGrid}>
          {hits?.items.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      </section>

      <section className={styles.delivery}>
        <div className={styles.delivery__inner}>
          <h2 className={styles.delivery__title}>{deliveryTitle}</h2>
          <div className={styles.delivery__steps}>
            {deliverySteps.map((step) => (
              <div key={step.number} className={styles.delivery__step}>
                <span className={styles.delivery__number}>{step.number}</span>
                <h3>{step.title}</h3>
                <p>{step.text}</p>
              </div>
            ))}
          </div>
          <LinkButton to="/delivery" variant="accent" size="md">
            Подробнее о доставке
          </LinkButton>
        </div>
      </section>
    </>
  );
}
