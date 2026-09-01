import { Link } from "react-router-dom";
import { Badge, Button } from "../ui";
import type { CatalogProduct } from "../../api/catalog";
import styles from "./ProductCard.module.scss";

interface ProductCardProps {
  product: CatalogProduct;
  badge?: string;
}

export function ProductCard({ product, badge }: ProductCardProps) {
  const priceLabel = `${product.retailPrice.toLocaleString("ru-RU", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} р./${product.unit}`;

  return (
    <article className={styles.card}>
      <Link to={`/products/${product.sku}`} className={styles.card__media}>
        {badge && (
          <Badge variant="accent" className={styles.card__badge}>
            {badge}
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
        <p className={styles.card__desc}>{product.description}</p>
        <div className={styles.card__footer}>
          <span className={styles.card__price}>{priceLabel}</span>
          <Button variant="secondary" size="sm">
            В корзину
          </Button>
        </div>
      </div>
    </article>
  );
}
