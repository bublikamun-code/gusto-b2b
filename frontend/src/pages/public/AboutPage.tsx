import styles from "./AboutPage.module.scss";

export default function AboutPage() {
  return (
    <section className={styles.about}>
      <header className={styles.about__header}>
        <span className={styles.about__eyebrow}>О нас</span>
        <h1 className={styles.about__title}>ГУСТО — мясной гастроном</h1>
      </header>

      <div className={styles.about__content}>
        <p>
          Мы работаем с фермерскими хозяйствами Минской области напрямую, без посредников. Наша
          задача — доставлять свежее мясо, птицу и яйца от фермы к вашему столу максимально быстро.
        </p>
        <p>
          Вся продукция охлаждённая, а не замороженная. Мы режем и фасуем под заказ, поэтому вы
          получаете именно тот вес и тот размер порции, которые нужны.
        </p>
        <p>
          Доставляем по Минску и ближайшему пригороду. Большинство заказов привозим в течение двух
          часов.
        </p>
      </div>

      <div className={styles.about__stats}>
        <div>
          <strong>12</strong>
          <span>ферм-партнёров</span>
        </div>
        <div>
          <strong>2 часа</strong>
          <span>среднее время доставки</span>
        </div>
        <div>
          <strong>0</strong>
          <span>заморозки и рассолов</span>
        </div>
      </div>
    </section>
  );
}
