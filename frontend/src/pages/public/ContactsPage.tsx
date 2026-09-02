import styles from "./ContactsPage.module.scss";

const CONTACTS = [
  { label: "Телефон", value: "+375 29 123-45-67", href: "tel:+375291234567" },
  { label: "Email", value: "info@gustomeat.by", href: "mailto:info@gustomeat.by" },
  { label: "Режим работы", value: "Ежедневно 9:00–21:00" },
  { label: "Доставка", value: "Минск и ближайший пригород" },
];

const REQUISITES = [
  { label: "Организация", value: "ЧТУП «ЛорСан»" },
  { label: "УНП", value: "191536521" },
  { label: "Адрес", value: "220028 г. Минск, ул. Бородинская, д. 1Б, пом. 14, РБ" },
];

export default function ContactsPage() {
  return (
    <section className={styles.contacts}>
      <header className={styles.contacts__header}>
        <span className={styles.contacts__eyebrow}>Контакты</span>
        <h1 className={styles.contacts__title}>Свяжитесь с нами</h1>
      </header>

      <div className={styles.contacts__layout}>
        <div className={styles.contacts__block}>
          <h2 className={styles.contacts__subtitle}>Связь</h2>
          <dl className={styles.contacts__list}>
            {CONTACTS.map((item) => (
              <div key={item.label} className={styles.contacts__row}>
                <dt>{item.label}</dt>
                <dd>
                  {item.href ? (
                    <a href={item.href} className={styles.contacts__link}>
                      {item.value}
                    </a>
                  ) : (
                    item.value
                  )}
                </dd>
              </div>
            ))}
          </dl>
        </div>

        <div className={styles.contacts__block}>
          <h2 className={styles.contacts__subtitle}>Реквизиты</h2>
          <dl className={styles.contacts__list}>
            {REQUISITES.map((item) => (
              <div key={item.label} className={styles.contacts__row}>
                <dt>{item.label}</dt>
                <dd>{item.value}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>

      <div className={styles.contacts__note}>
        <p>
          Для оформления B2B-доставки и подключения корпоративного кабинета напишите нам на
          info@gustomeat.by — ответим в течение рабочего дня.
        </p>
      </div>
    </section>
  );
}
