import { forwardRef, useCallback, useEffect, useId, useRef, useState, type SelectHTMLAttributes } from "react";
import { createPortal } from "react-dom";
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

type ChangeEvent = Parameters<NonNullable<SelectProps["onChange"]>>[0];

// Попап рендерится порталом в body (position: fixed у триггера): внутри таблиц
// абсолютный попап в ячейке ломает вёрстку свёрнутой таблицы в Chromium,
// а overflow-контейнеры (таблицы, модалки) обрезают обычный absolute-попап.
const POPUP_Z_INDEX = 500; // выше модалок (100) и шапок, ниже тостов (1000)

// Кастомный дропдаун вместо нативного <select>: нативный popup рисуется ОС
// и не стилизуется. Публичный API прежний (value/onChange-событие, register),
// onChange вызывается с шимом { target: { name, value } }.
export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  (
    { label, error, options, placeholder, id, className, value, onChange, onBlur, name, disabled, ...rest },
    ref,
  ) => {
    const autoId = useId();
    const selectId = id ?? autoId;
    const listId = `${selectId}-list`;
    const rootRef = useRef<HTMLDivElement>(null);
    const buttonRef = useRef<HTMLButtonElement | null>(null);
    const popupRef = useRef<HTMLUListElement | null>(null);
    const [open, setOpen] = useState(false);
    const [popupPos, setPopupPos] = useState<{ left: number; top: number; width: number } | null>(null);
    const [activeIndex, setActiveIndex] = useState(-1);

    const list = placeholder
      ? [{ value: "", label: placeholder }, ...options]
      : options;
    const current = list.find((option) => option.value === (value ?? ""));
    const displayLabel = current
      ? current.label
      : placeholder ?? options[0]?.label ?? "";
    const isPlaceholder = !current;

    const place = useCallback(() => {
      const rect = buttonRef.current?.getBoundingClientRect();
      if (!rect) return;
      setPopupPos({ left: rect.left, top: rect.bottom + 4, width: rect.width });
    }, []);

    const close = useCallback(
      (focusBack: boolean) => {
        setOpen(false);
        onBlur?.({ target: { name, value }, relatedTarget: null } as unknown as Parameters<
          NonNullable<SelectProps["onBlur"]>
        >[0]);
        if (focusBack) buttonRef.current?.focus();
      },
      [onBlur, name, value],
    );

    useEffect(() => {
      if (!open) return;
      const onPointerDown = (event: MouseEvent) => {
        const target = event.target as Node;
        if (!rootRef.current?.contains(target) && !popupRef.current?.contains(target)) close(false);
      };
      document.addEventListener("mousedown", onPointerDown);
      return () => document.removeEventListener("mousedown", onPointerDown);
    }, [open, close]);

    // попап следует за триггером при скролле и ресайзе, пока открыт
    useEffect(() => {
      if (!open) return;
      window.addEventListener("scroll", place, true);
      window.addEventListener("resize", place);
      return () => {
        window.removeEventListener("scroll", place, true);
        window.removeEventListener("resize", place);
      };
    }, [open, place]);

    // активный пункт всегда виден в скроллящемся списке
    useEffect(() => {
      if (!open) return;
      popupRef.current
        ?.querySelector(`[data-index="${activeIndex}"]`)
        ?.scrollIntoView?.({ block: "nearest" });
    }, [open, activeIndex]);

    const openList = () => {
      if (disabled) return;
      const startIndex = list.findIndex((option) => option.value === (value ?? ""));
      setActiveIndex(startIndex >= 0 ? startIndex : 0);
      place();
      setOpen(true);
    };

    const commit = (option: SelectOption) => {
      onChange?.({ target: { name, value: option.value } } as unknown as ChangeEvent);
      close(true);
    };

    const onTriggerKeyDown = (event: React.KeyboardEvent) => {
      if (!open) {
        if (["ArrowDown", "ArrowUp", "Enter", " "].includes(event.key)) {
          event.preventDefault();
          openList();
        }
        return;
      }
      if (event.key === "Escape") {
        event.stopPropagation();
        close(true);
      } else if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        const delta = event.key === "ArrowDown" ? 1 : -1;
        setActiveIndex((index) => Math.min(list.length - 1, Math.max(0, index + delta)));
      } else if (event.key === "Home") {
        event.preventDefault();
        setActiveIndex(0);
      } else if (event.key === "End") {
        event.preventDefault();
        setActiveIndex(list.length - 1);
      } else if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        const option = list[activeIndex];
        if (option) commit(option);
      } else if (event.key === "Tab") {
        close(false);
      }
    };

    return (
      <div className={[styles.wrapper, className].filter(Boolean).join(" ")}>
        {label && (
          <label className={styles.label} htmlFor={selectId}>
            {label}
          </label>
        )}
        <div className={styles.field} ref={rootRef}>
          <button
            {...(rest as unknown as React.ComponentProps<"button">)}
            type="button"
            id={selectId}
            ref={(node) => {
              buttonRef.current = node;
              const forwarded = ref as React.MutableRefObject<HTMLButtonElement | null>;
              if (typeof ref === "function") ref(node as unknown as HTMLSelectElement);
              else if (forwarded) forwarded.current = node;
            }}
            role="combobox"
            aria-expanded={open}
            aria-haspopup="listbox"
            aria-controls={open ? listId : undefined}
            aria-activedescendant={open && activeIndex >= 0 ? `${listId}-${activeIndex}` : undefined}
            aria-invalid={error ? true : undefined}
            className={[styles.trigger, error ? styles.invalid : ""].filter(Boolean).join(" ")}
            disabled={disabled}
            onClick={() => (open ? close(true) : openList())}
            onKeyDown={onTriggerKeyDown}
          >
            <span className={isPlaceholder ? styles.placeholder : undefined}>{displayLabel}</span>
          </button>
          <span className={styles.arrow} aria-hidden>
            ▾
          </span>
          {open &&
            popupPos &&
            createPortal(
              <ul
                className={styles.popup}
                role="listbox"
                id={listId}
                ref={popupRef}
                style={{
                  position: "fixed",
                  left: popupPos.left,
                  top: popupPos.top,
                  width: popupPos.width,
                  zIndex: POPUP_Z_INDEX,
                }}
              >
                {list.map((option, index) => {
                  const selected = option.value === (value ?? "");
                  return (
                    <li
                      key={option.value}
                      id={`${listId}-${index}`}
                      role="option"
                      aria-selected={selected}
                      data-index={index}
                      className={[
                        styles.option,
                        selected ? styles.optionSelected : "",
                        index === activeIndex ? styles.optionActive : "",
                      ]
                        .filter(Boolean)
                        .join(" ")}
                      onPointerMove={() => setActiveIndex(index)}
                      onClick={() => commit(option)}
                    >
                      <span>{option.label}</span>
                      {selected && (
                        <span className={styles.check} aria-hidden>
                          ✓
                        </span>
                      )}
                    </li>
                  );
                })}
              </ul>,
              document.body,
            )}
        </div>
        {error && <p className={styles.error}>{error}</p>}
      </div>
    );
  },
);

Select.displayName = "Select";
