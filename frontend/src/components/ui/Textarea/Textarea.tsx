import { forwardRef, type TextareaHTMLAttributes, useId } from "react";
import styles from "./Textarea.module.scss";

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ label, error, id, className, ...rest }, ref) => {
    const autoId = useId();
    const areaId = id ?? autoId;

    return (
      <div className={[styles.wrapper, className].filter(Boolean).join(" ")}>
        {label && (
          <label className={styles.label} htmlFor={areaId}>
            {label}
          </label>
        )}
        <textarea
          id={areaId}
          ref={ref}
          className={[styles.area, error ? styles.invalid : ""].filter(Boolean).join(" ")}
          aria-invalid={error ? true : undefined}
          {...rest}
        />
        {error && <p className={styles.error}>{error}</p>}
      </div>
    );
  },
);

Textarea.displayName = "Textarea";
