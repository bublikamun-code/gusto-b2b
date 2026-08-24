import styles from "./Pagination.module.scss";

export interface PaginationProps {
  page: number; // 1-based
  size: number;
  total: number; // всего записей
  onChange: (page: number) => void;
}

function buildPages(page: number, totalPages: number): (number | "dots")[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }
  const pages: (number | "dots")[] = [1];
  const start = Math.max(2, page - 1);
  const end = Math.min(totalPages - 1, page + 1);
  if (start > 2) pages.push("dots");
  for (let current = start; current <= end; current++) pages.push(current);
  if (end < totalPages - 1) pages.push("dots");
  pages.push(totalPages);
  return pages;
}

export function Pagination({ page, size, total, onChange }: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(total / size));
  if (total === 0) return null;

  const from = (page - 1) * size + 1;
  const to = Math.min(total, page * size);

  return (
    <div className={styles.wrapper}>
      <span className={styles.summary}>
        {from}–{to} из {total}
      </span>
      <div className={styles.pages}>
        <button
          type="button"
          className={styles.nav}
          disabled={page <= 1}
          onClick={() => onChange(page - 1)}
          aria-label="Предыдущая страница"
        >
          ‹
        </button>
        {buildPages(page, totalPages).map((item, index) =>
          item === "dots" ? (
            <span key={`dots-${index}`} className={styles.dots}>
              …
            </span>
          ) : (
            <button
              type="button"
              key={item}
              className={[styles.page, item === page ? styles.active : ""]
                .filter(Boolean)
                .join(" ")}
              onClick={() => onChange(item)}
            >
              {item}
            </button>
          ),
        )}
        <button
          type="button"
          className={styles.nav}
          disabled={page >= totalPages}
          onClick={() => onChange(page + 1)}
          aria-label="Следующая страница"
        >
          ›
        </button>
      </div>
    </div>
  );
}
