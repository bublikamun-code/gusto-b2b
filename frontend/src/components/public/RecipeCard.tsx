import { Link } from "react-router-dom";
import type { Recipe } from "../../api/aiAdvisor";
import { formatMoney } from "../../lib/format";
import styles from "./RecipeCard.module.scss";

interface RecipeCardProps {
  recipe: Recipe;
  /** Компактная карточка под ответом советника. */
  compact?: boolean;
}

/** Цена приходит из рецепта строкой; показываем в том же формате, что и витрина. */
function priceLabel(price: string | null | undefined): string {
  const value = Number(price);
  return price && Number.isFinite(value) ? formatMoney(value) : "по запросу";
}

export function RecipeCard({ recipe, compact = false }: RecipeCardProps) {
  const products = recipe.products ?? [];

  return (
    <article className={[styles.card, compact ? styles.card_compact : ""].join(" ")}>
      <header className={styles.card__header}>
        <h4 className={styles.card__title}>{recipe.title}</h4>
        {recipe.cookingMinutes ? (
          <span className={styles.card__time}>{recipe.cookingMinutes} мин</span>
        ) : null}
      </header>

      {!compact && <p className={styles.card__summary}>{recipe.summary}</p>}

      <ul className={styles.card__products}>
        {products.map((product) => (
          <li key={product.sku} className={styles.card__product}>
            <Link to={product.productUrl} className={styles.card__productName}>
              {product.name}
            </Link>
            <span className={styles.card__productMeta}>
              {product.quantity ? `${product.quantity} · ` : ""}
              {priceLabel(product.retailPrice)}
              {product.stockStatus === "IN_STOCK" ? "" : " · под заказ"}
            </span>
          </li>
        ))}
      </ul>

      {!compact && recipe.difficulty ? (
        <footer className={styles.card__footer}>
          <span className={styles.card__difficulty}>Сложность: {recipe.difficulty}</span>
        </footer>
      ) : null}
    </article>
  );
}
