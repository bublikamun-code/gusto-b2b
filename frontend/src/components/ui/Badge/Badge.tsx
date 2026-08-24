import type { HTMLAttributes } from "react";
import styles from "./Badge.module.scss";

export type BadgeVariant = "success" | "warning" | "accent" | "neutral" | "outline";

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
}

export function Badge({ variant = "neutral", className, children, ...rest }: BadgeProps) {
  return (
    <span className={[styles.badge, styles[variant], className].filter(Boolean).join(" ")} {...rest}>
      {children}
    </span>
  );
}
