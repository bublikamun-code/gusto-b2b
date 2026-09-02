import styles from "./PrivacyPage.module.scss";

const SECTIONS = [
  {
    title: "Какие данные мы собираем",
    text: "При регистрации и оформлении заказов мы получаем от вас: имя, email, номер телефона, название компании и УНП (для юрлиц), адрес доставки. Платёжные реквизиты карты на наших серверах не хранятся — оплата происходит при получении.",
  },
  {
    title: "Зачем мы используем данные",
    text: "Данные нужны для обработки заказов, расчёта персональных цен, выставления счетов и накладных, а также для связи с вами по статусу заказа. Мы не передаём данные третьим лицам, кроме случаев, предусмотренных законодательством РБ.",
  },
  {
    title: "Хранение и защита",
    text: "Данные хранятся на серверах в зашифрованном виде, доступ имеют только уполномоченные сотрудники. Пароли хранятся только в виде криптографических хэшей. Доступ к приватным файлам (например, документам компании) ограничен проверкой прав.",
  },
  {
    title: "Ваши права",
    text: "Вы можете запросить выгрузку, исправление или удаление своих персональных данных, написав на info@gustomeat.by. Мы отвечаем на такие запросы в течение 30 дней.",
  },
];

export default function PrivacyPage() {
  return (
    <section className={styles.privacy}>
      <header className={styles.privacy__header}>
        <span className={styles.privacy__eyebrow}>Правовая информация</span>
        <h1 className={styles.privacy__title}>Политика конфиденциальности</h1>
      </header>

      <div className={styles.privacy__content}>
        {SECTIONS.map((section) => (
          <article key={section.title} className={styles.privacy__section}>
            <h2>{section.title}</h2>
            <p>{section.text}</p>
          </article>
        ))}

        <p className={styles.privacy__updated}>Последнее обновление: сентябрь 2026 г.</p>
      </div>
    </section>
  );
}
