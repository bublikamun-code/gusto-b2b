import styles from "./DeliveryPage.module.scss";

const DELIVERY_STEPS = [
  {
    title: "Приём заказа",
    text: "Оформляйте заказ на сайте до 14:00 ежедневно. После подтверждения собираем вашу корзину свежим мясом и птицей.",
  },
  {
    title: "Нарезка и фасовка",
    text: "Режем и взвешиваем под заказ, вакуумируем порции. Вы получаете точно то количество, которое заказывали.",
  },
  {
    title: "Доставка по Минску",
    text: "Привозим заказ в течение 2 часов в термо-рюкзаке. Оплата при получении наличными или картой.",
  },
];

export default function DeliveryPage() {
  return (
    <section className={styles.delivery}>
      <header className={styles.delivery__header}>
        <span className={styles.delivery__eyebrow}>Доставка</span>
        <h1 className={styles.delivery__title}>От фермы к столу за 2 часа</h1>
      </header>

      <div className={styles.delivery__steps}>
        {DELIVERY_STEPS.map((step) => (
          <div key={step.title} className={styles.delivery__step}>
            <h2>{step.title}</h2>
            <p>{step.text}</p>
          </div>
        ))}
      </div>

      <div className={styles.delivery__note}>
        <p>
          Минимальная сумма заказа — 30 рублей. Доставка по городу Минску — бесплатно при заказе от
          50 рублей. Заказы, оформленные после 14:00, доставляем на следующий день.
        </p>
      </div>
    </section>
  );
}
