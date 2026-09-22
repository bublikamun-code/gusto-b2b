import styles from "./QuantityStepper.module.scss";

export interface QuantityStepperProps {
  /** черновик значения как в input (строка) */
  value: string;
  onChange: (next: string) => void;
  /** вызывается после −/+ с итоговым значением: страница сама решает, коммитить ли */
  onStep?: (next: string) => void;
  onBlur?: () => void;
  onKeyDown?: (event: React.KeyboardEvent<HTMLInputElement>) => void;
  min?: number;
  max?: number;
  step?: number;
  disabled?: boolean;
  ariaLabel?: string;
  id?: string;
}

const round3 = (n: number) => Math.round(n * 1000) / 1000;

// Поле количества со степпером −/+: высота как у строки таблицы,
// окошко ввода уже обычного инпута.
export function QuantityStepper({
  value,
  onChange,
  onStep,
  onBlur,
  onKeyDown,
  min = 0,
  max,
  step = 1,
  disabled,
  ariaLabel,
  id,
}: QuantityStepperProps) {
  const numeric = Number.parseFloat(value.replace(",", "."));

  const apply = (next: number) => {
    const clamped = Math.min(max ?? Number.POSITIVE_INFINITY, Math.max(min, round3(next)));
    const formatted = String(clamped);
    onChange(formatted);
    onStep?.(formatted);
  };

  const stepBy = (dir: -1 | 1) => {
    const base = Number.isFinite(numeric) ? numeric : min;
    apply(base + dir * step);
  };

  return (
    <span className={styles.stepper}>
      <button
        type="button"
        className={styles.button}
        aria-label="Уменьшить количество"
        disabled={disabled || !Number.isFinite(numeric) || numeric <= min}
        onClick={() => stepBy(-1)}
      >
        −
      </button>
      <input
        id={id}
        className={styles.input}
        type="number"
        inputMode="decimal"
        value={value}
        min={min}
        max={max}
        step={step}
        disabled={disabled}
        aria-label={ariaLabel}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
        onKeyDown={onKeyDown}
      />
      <button
        type="button"
        className={styles.button}
        aria-label="Увеличить количество"
        disabled={disabled}
        onClick={() => stepBy(1)}
      >
        +
      </button>
    </span>
  );
}
