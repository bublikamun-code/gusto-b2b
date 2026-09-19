import { useEffect, useState } from "react";
import { getPublicPage, type CmsPage } from "../../api/cms";
import styles from "./CmsPageView.module.scss";

/**
 * Публичная CMS-страница (S37): контент из БД по slug; используется
 * для «О нас» и «Доставка» вместо захардкоженного текста.
 */
export default function CmsPageView({ slug, fallbackTitle }: { slug: string; fallbackTitle: string }) {
  const [page, setPage] = useState<CmsPage | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    getPublicPage(slug)
      .then(setPage)
      .catch(() => setError(true));
  }, [slug]);

  return (
    <div className={styles.page}>
      <h1>{page?.title ?? fallbackTitle}</h1>
      {error && <p className={styles.hint}>Раздел готовится к публикации.</p>}
      {page && (
        <div className={styles.body}>
          {page.body.split("\n\n").map((paragraph, index) => (
            <p key={index}>{paragraph}</p>
          ))}
        </div>
      )}
    </div>
  );
}
