import { type InputHTMLAttributes, useId } from "react";
import styles from "./Input.module.scss";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export function Input({ label, error, id, className, ...rest }: InputProps) {
  const autoId = useId();
  const inputId = id ?? autoId;

  return (
    <div className={[styles.wrapper, className].filter(Boolean).join(" ")}>
      {label && (
        <label className={styles.label} htmlFor={inputId}>
          {label}
        </label>
      )}
      <input
        id={inputId}
        className={[styles.input, error ? styles.invalid : ""].filter(Boolean).join(" ")}
        aria-invalid={error ? true : undefined}
        {...rest}
      />
      {error && <p className={styles.error}>{error}</p>}
    </div>
  );
}
