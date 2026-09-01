import { forwardRef, type SelectHTMLAttributes, useId } from "react";
import styles from "./Select.module.scss";

export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  options: SelectOption[];
  placeholder?: string;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ label, error, options, placeholder, id, className, ...rest }, ref) => {
    const autoId = useId();
    const selectId = id ?? autoId;

    return (
      <div className={[styles.wrapper, className].filter(Boolean).join(" ")}>
        {label && (
          <label className={styles.label} htmlFor={selectId}>
            {label}
          </label>
        )}
        <div className={styles.field}>
          <select
            id={selectId}
            ref={ref}
            className={[styles.select, error ? styles.invalid : ""].filter(Boolean).join(" ")}
            {...rest}
          >
            {placeholder && <option value="">{placeholder}</option>}
            {options.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <span className={styles.arrow} aria-hidden>
            ▾
          </span>
        </div>
        {error && <p className={styles.error}>{error}</p>}
      </div>
    );
  },
);

Select.displayName = "Select";
