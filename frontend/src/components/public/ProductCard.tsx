import { Link } from "react-router-dom";
import { Badge, Button } from "../ui";
import type { CatalogProduct } from "../../api/catalog";
import { stockStatusLabel } from "../../lib/stockStatus";
import { useCartStore } from "../../store/cartStore";
import styles from "./ProductCard.module.scss";

interface ProductCardProps {
  product: CatalogProduct;
  /** Переопределяет витринный бейдж (isHit/isNew). */
  badge?: string;
}

// Бейджи из данных: ХИТ приоритетнее НОВИНКА (S19.1)
function showcaseBadge(product: CatalogProduct): string | undefined {
  if (product.isHit) return "ХИТ";
  if (product.isNew) return "НОВИНКА";
  return undefined;
}

export function ProductCard({ product, badge }: ProductCardProps) {
  const priceLabel = `${product.retailPrice.toLocaleString("ru-RU", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} р./${product.unit}`;
  const effectiveBadge = badge ?? showcaseBadge(product);

  return (
    <article className={styles.card}>
      <Link to={`/products/${product.sku}`} className={styles.card__media}>
        {effectiveBadge && (
          <Badge variant="accent" className={styles.card__badge}>
            {effectiveBadge}
          </Badge>
        )}
        {product.imageUrl ? (
          <img
            src={product.imageUrl}
            alt={product.name}
            className={styles.card__image}
            loading="lazy"
          />
        ) : (
          <div className={styles.card__placeholder} aria-hidden />
        )}
      </Link>
      <div className={styles.card__body}>
        <Link to={`/products/${product.sku}`} className={styles.card__name}>
          {product.name}
        </Link>
        <p className={styles.card__desc}>
          {product.stockStatus && product.stockStatus !== "IN_STOCK"
            ? `${stockStatusLabel(product.stockStatus)} · `
            : ""}
          {product.description}
        </p>
        <div className={styles.card__footer}>
          <span className={styles.card__price}>{priceLabel}</span>
          <Button
            variant="secondary"
            size="sm"
            onClick={() =>
              useCartStore.getState().addItem(
                {
                  productId: product.id,
                  sku: product.sku,
                  name: product.name,
                  unit: product.unit,
                  price: product.retailPrice,
                  step: product.weightStep ?? null,
                },
                1,
              )
            }
          >
            В корзину
          </Button>
        </div>
      </div>
    </article>
  );
}
