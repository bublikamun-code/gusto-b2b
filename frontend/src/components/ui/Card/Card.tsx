import type { HTMLAttributes, ReactNode } from "react";
import styles from "./Card.module.scss";

export interface CardProps extends Omit<HTMLAttributes<HTMLDivElement>, "title"> {
  title?: ReactNode;
  actions?: ReactNode;
  accent?: boolean;
  /**
   * Класс для обёртки контента. className попадает на корень карточки, а дети
   * лежат на уровень ниже — раскладку (flex/grid/gap) нужно вешать именно сюда,
   * иначе она молча ничего не делает (S44).
   */
  bodyClassName?: string;
  /**
   * Уровень заголовка карточки. По умолчанию h3 — карточка почти всегда вложена
   * в секцию с h2. На дашбордах карточка идёт сразу за h1 страницы, и h1 → h3 —
   * пропуск уровня (WCAG 1.3.1): там передаём titleAs="h2".
   */
  titleAs?: "h2" | "h3" | "h4";
}

export function Card({
  title,
  actions,
  accent = false,
  className,
  bodyClassName,
  titleAs: TitleTag = "h3",
  children,
  ...rest
}: CardProps) {
  return (
    <div
      className={[styles.card, accent ? styles.accent : "", className].filter(Boolean).join(" ")}
      {...rest}
    >
      {(title || actions) && (
        <header className={styles.header}>
          {title && <TitleTag className={styles.title}>{title}</TitleTag>}
          {actions && <div className={styles.actions}>{actions}</div>}
        </header>
      )}
      <div className={[styles.body, bodyClassName].filter(Boolean).join(" ")}>{children}</div>
    </div>
  );
}
