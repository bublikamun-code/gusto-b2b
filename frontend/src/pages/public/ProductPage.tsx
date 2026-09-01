import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Button } from "../../components/ui";
import { getProduct } from "../../api/catalog";
import styles from "./ProductPage.module.scss";

export default function ProductPage() {
  const { sku } = useParams<{ sku: string }>();

  const { data: product, isLoading, error } = useQuery({
    queryKey: ["product", sku],
    queryFn: () => getProduct(sku!),
    enabled: Boolean(sku),
  });

  if (isLoading) {
    return (
      <section className={styles.product}>
        <p className={styles.product__status}>Загружаем товар…</p>
      </section>
    );
  }

  if (error || !product) {
    return (
      <section className={styles.product}>
        <p className={styles.product__status}>Товар не найден.</p>
        <Link to="/catalog">
          <Button variant="primary">В каталог</Button>
        </Link>
      </section>
    );
  }

  const priceLabel = `${product.retailPrice.toLocaleString("ru-RU", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} р./${product.unit}`;

  return (
    <section className={styles.product}>
      <div className={styles.product__breadcrumbs}>
        <Link to="/">Главная</Link>
        <span>/</span>
        <Link to="/catalog">Каталог</Link>
        <span>/</span>
        <span>{product.name}</span>
      </div>

      <div className={styles.product__layout}>
        <div className={styles.product__media}>
          {product.imageUrl ? (
            <img
              src={product.imageUrl}
              alt={product.name}
              className={styles.product__image}
            />
          ) : (
            <div className={styles.product__placeholder} aria-hidden />
          )}
        </div>

        <div className={styles.product__info}>
          <span className={styles.product__category}>{product.category?.name}</span>
          {product.brand && <span className={styles.product__brand}>{product.brand.name}</span>}
          <h1 className={styles.product__name}>{product.name}</h1>
          <p className={styles.product__desc}>{product.description}</p>

          <div className={styles.product__priceRow}>
            <span className={styles.product__price}>{priceLabel}</span>
            <Button variant="primary" size="lg">
              В корзину
            </Button>
          </div>

          <dl className={styles.product__props}>
            <div>
              <dt>Артикул</dt>
              <dd>{product.sku}</dd>
            </div>
            <div>
              <dt>Единица</dt>
              <dd>{product.unit}</dd>
            </div>
            {product.brand && (
              <div>
                <dt>Бренд</dt>
                <dd>{product.brand.name}</dd>
              </div>
            )}
          </dl>
        </div>
      </div>
    </section>
  );
}
