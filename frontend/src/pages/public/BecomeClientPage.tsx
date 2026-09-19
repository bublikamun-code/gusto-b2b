import { useState } from "react";
import { Button, Card, Input, Select, Textarea, useToast } from "../../components/ui";
import {
  createSiteRequest,
  validateSiteRequest,
  type SiteRequestType,
} from "../../api/siteRequests";
import { openChat } from "../../chat";
import styles from "./BecomeClientPage.module.scss";

const TYPE_OPTIONS = [
  { value: "WHOLESALE", label: "Оптовые поставки (юрлицо)" },
  { value: "RETAIL", label: "Розничные заказы" },
  { value: "CALLBACK", label: "Перезвоните мне" },
  { value: "OTHER", label: "Другое" },
];

/** «Стать клиентом» (S34): публичная форма → заявка в CRM + лид менеджеру. */
export default function BecomeClientPage() {
  const { push } = useToast();

  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [type, setType] = useState<SiteRequestType>("WHOLESALE");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const submit = async () => {
    const error = validateSiteRequest({ name, phone, email, type, message });
    if (error) {
      push(error, "error");
      return;
    }
    setSubmitting(true);
    try {
      await createSiteRequest({
        name: name.trim(),
        phone: phone.trim() || undefined,
        email: email.trim() || undefined,
        type,
        message: message.trim() || undefined,
      });
      setDone(true);
      push("Заявка отправлена — менеджер свяжется с вами", "success");
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return (
      <div className={styles.page}>
        <Card className={styles.card}>
          <h1>Заявка принята</h1>
          <p>
            Спасибо! Менеджер свяжется с вами в рабочее время. Вопросы можно задать
            прямо в чате.
          </p>
          <div className={styles.actions}>
            <Button variant="accent" onClick={openChat}>
              Открыть чат
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <h1>Стать клиентом</h1>
        <p className={styles.hint}>
          Оставьте заявку — менеджер подберёт условия поставок для вашего бизнеса.
        </p>
        <Input label="Имя" value={name} onChange={(e) => setName(e.target.value)} />
        <Input label="Телефон" value={phone} onChange={(e) => setPhone(e.target.value)} />
        <Input label="Email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Select
          label="Что вас интересует"
          options={TYPE_OPTIONS}
          value={type}
          onChange={(e) => setType(e.target.value as SiteRequestType)}
        />
        <Textarea
          label="Сообщение"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          placeholder="Номенклатура, объёмы, сроки…"
        />
        <div className={styles.actions}>
          <Button variant="accent" loading={submitting} onClick={submit}>
            Отправить заявку
          </Button>
        </div>
      </Card>
    </div>
  );
}
