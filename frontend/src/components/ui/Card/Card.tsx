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
}

export function Card({
  title,
  actions,
  accent = false,
  className,
  bodyClassName,
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
          {title && <h3 className={styles.title}>{title}</h3>}
          {actions && <div className={styles.actions}>{actions}</div>}
        </header>
      )}
      <div className={[styles.body, bodyClassName].filter(Boolean).join(" ")}>{children}</div>
    </div>
  );
}
