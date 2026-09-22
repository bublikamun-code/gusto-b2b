import { type AnchorHTMLAttributes } from "react";
import { Link } from "react-router-dom";
import buttonStyles from "../Button/Button.module.scss";

type Variant = "primary" | "secondary" | "accent";
type Size = "sm" | "md" | "lg";

export interface LinkButtonProps extends AnchorHTMLAttributes<HTMLAnchorElement> {
  to: string;
  variant?: Variant;
  size?: Size;
  block?: boolean;
}

export function LinkButton({
  to,
  variant = "primary",
  size = "md",
  block = false,
  className,
  children,
  ...rest
}: LinkButtonProps) {
  const classes = [
    buttonStyles.button,
    buttonStyles[variant],
    buttonStyles[size],
    block ? buttonStyles.block : "",
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <Link to={to} className={classes} {...rest}>
      {children}
    </Link>
  );
}
