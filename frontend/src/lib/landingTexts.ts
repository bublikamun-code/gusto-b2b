import type { DeliveryStep, HeroSettings } from "../api/adminOperations";

/**
 * Тексты лендинга (S38): значения из настроек админки с дефолтами витрины.
 * Пустая настройка или пустая строка — показываем дефолт.
 */

export interface PublicLanding {
  hero: Partial<HeroSettings>;
  delivery: { title?: string; steps?: DeliveryStep[] };
}

const DEFAULT_HERO: HeroSettings = {
  eyebrow: "Интернет-магазин · Минск",
  title: "Свежая поставка каждое утро",
  text: "Работаем с фермерскими хозяйствами Минской области напрямую, без посредников. Мясо охлаждённое, не заморозка. Привозим заказы за два часа — от фермы к вашему столу.",
};

export const DEFAULT_DELIVERY_TITLE = "Как мы доставляем";

export const DEFAULT_DELIVERY_STEPS: DeliveryStep[] = [
  {
    number: "01",
    title: "Заказ до 14:00",
    text: "Принимаем заказы каждый день до 14:00. Доставка в тот же день — от фермы к вашему столу.",
  },
  {
    number: "02",
    title: "Режем и взвешиваем",
    text: "Готовим мясо и птицу под заказ: свежая нарезка, точный вес, фасовка в вакуум.",
  },
  {
    number: "03",
    title: "Привозим за 2 часа",
    text: "Доставляем по Минску и ближайшему пригороду в термо-рюкзаке. Мясо остаётся прохладным.",
  },
];

function pick(value: string | undefined, fallback: string): string {
  return value && value.trim() !== "" ? value : fallback;
}

export function resolveLanding(landing?: PublicLanding | null): {
  hero: HeroSettings;
  deliveryTitle: string;
  deliverySteps: DeliveryStep[];
} {
  return {
    hero: {
      eyebrow: pick(landing?.hero?.eyebrow, DEFAULT_HERO.eyebrow),
      title: pick(landing?.hero?.title, DEFAULT_HERO.title),
      text: pick(landing?.hero?.text, DEFAULT_HERO.text),
    },
    deliveryTitle: pick(landing?.delivery?.title, DEFAULT_DELIVERY_TITLE),
    deliverySteps:
      landing?.delivery?.steps && landing.delivery.steps.length > 0
        ? landing.delivery.steps
        : DEFAULT_DELIVERY_STEPS,
  };
}
