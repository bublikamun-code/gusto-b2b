import { useCallback, useEffect, useState } from "react";
import { Button, Card, Input, Select, useToast } from "../../components/ui";
import {
  getSettings,
  updateAuthSettings,
  updateDocumentSettings,
  updateLandingSettings,
  updateNotificationSettings,
  updateSellerSettings,
  updateStockSettings,
  type SettingsResponse,
} from "../../api/adminOperations";
import styles from "./AdminPages.module.scss";
import settingsStyles from "./AdminSettingsPage.module.scss";

const NOTIFICATION_EVENTS: Record<string, string> = {
  ORDER_CREATED: "Новый заказ",
  ORDER_STATUS_CHANGED: "Смена статуса заказа",
  INVOICE_ISSUED: "Выставлен счёт",
  SITE_REQUEST_CREATED: "Заявка с сайта",
};

/** Настройки операционного центра (S38): реквизиты, документы, уведомления,
 * склад, регистрация, витрина. SMTP остаётся в .env (S33). */
export default function AdminSettingsPage() {
  const { push } = useToast();
  const [settings, setSettings] = useState<SettingsResponse | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    getSettings()
      .then(setSettings)
      .catch((err) => push((err as Error).message, "error"));
  }, [push]);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (action: () => Promise<SettingsResponse>, message: string) => {
    setBusy(true);
    try {
      setSettings(await action());
      push(message, "success");
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  if (!settings) {
    return <div className={styles.page}>Загрузка настроек…</div>;
  }

  return (
    <div className={settingsStyles.stack}>
      <h1 className={styles.title}>Настройки</h1>
      <SellerSection busy={busy} settings={settings} onSaved={run} />
      <DocumentsSection busy={busy} settings={settings} onSaved={run} />
      <NotificationsSection busy={busy} settings={settings} onSaved={run} />
      <StockSection busy={busy} settings={settings} onSaved={run} />
      <AuthSection busy={busy} settings={settings} onSaved={run} />
      <LandingSection busy={busy} settings={settings} onSaved={run} />
    </div>
  );
}

type SavedHandler = (
  action: () => Promise<SettingsResponse>,
  message: string,
) => Promise<void>;

function SellerSection({
  busy,
  settings,
  onSaved,
}: {
  busy: boolean;
  settings: SettingsResponse;
  onSaved: SavedHandler;
}) {
  const [form, setForm] = useState(settings.seller);
  useEffect(() => setForm(settings.seller), [settings.seller]);

  return (
    <Card title="Реквизиты продавца">
      <div className={settingsStyles.fields}>
        <p className={styles.hint}>
          Попадают в снапшот новых счетов и накладных (PDF); уже созданные документы не меняются.
        </p>
        <div className={styles.filters}>
          <Input
            label="Организация"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
          <Input
            label="УНП"
            value={form.unp}
            onChange={(e) => setForm({ ...form, unp: e.target.value })}
          />
        </div>
        <Input
          label="Адрес"
          value={form.address}
          onChange={(e) => setForm({ ...form, address: e.target.value })}
        />
        <div className={styles.filters}>
          <Input
            label="Банковский счёт"
            value={form.bankAccount}
            onChange={(e) => setForm({ ...form, bankAccount: e.target.value })}
          />
          <Input
            label="Банк"
            value={form.bankName}
            onChange={(e) => setForm({ ...form, bankName: e.target.value })}
          />
          <Input
            label="БИК"
            value={form.bankBic}
            onChange={(e) => setForm({ ...form, bankBic: e.target.value })}
          />
        </div>
      </div>
      <Button
        variant="accent"
        loading={busy}
        disabled={!form.name.trim() || !form.unp.trim()}
        onClick={() =>
          onSaved(() => updateSellerSettings(form), "Реквизиты сохранены")
        }
      >
        Сохранить реквизиты
      </Button>
    </Card>
  );
}

function DocumentsSection({
  busy,
  settings,
  onSaved,
}: {
  busy: boolean;
  settings: SettingsResponse;
  onSaved: SavedHandler;
}) {
  const [form, setForm] = useState(settings.documents);
  useEffect(() => setForm(settings.documents), [settings.documents]);

  return (
    <Card title="Документы">
      <div className={settingsStyles.fields}>
        <p className={styles.hint}>
          Серии ТН/ТТН и НДС по умолчанию (2.2, 2.3). Номера продолжаются по сквозной нумерации года.
        </p>
        <div className={styles.filters}>
          <Input
            label="Серия ТТН"
            value={form.seriesTtn}
            onChange={(e) => setForm({ ...form, seriesTtn: e.target.value })}
          />
          <Input
            label="Серия ТН"
            value={form.seriesTn}
            onChange={(e) => setForm({ ...form, seriesTn: e.target.value })}
          />
          <Input
            label="НДС по умолчанию, %"
            type="number"
            value={String(form.vatDefault)}
            onChange={(e) =>
              setForm({ ...form, vatDefault: Number(e.target.value) })
            }
          />
        </div>
      </div>
      <Button
        variant="accent"
        loading={busy}
        disabled={!form.seriesTtn.trim() || !form.seriesTn.trim()}
        onClick={() => onSaved(() => updateDocumentSettings(form), "Настройки документов сохранены")}
      >
        Сохранить документы
      </Button>
    </Card>
  );
}

function NotificationsSection({
  busy,
  settings,
  onSaved,
}: {
  busy: boolean;
  settings: SettingsResponse;
  onSaved: SavedHandler;
}) {
  const notifications = settings.notifications;
  const [token, setToken] = useState("");
  const [rules, setRules] = useState<Record<string, boolean>>(notifications.rules);
  useEffect(() => setRules(notifications.rules), [notifications.rules]);

  return (
    <Card title="Уведомления">
      <div className={settingsStyles.fields}>
        <p className={styles.hint}>
          Токен Telegram-бота хранится в настройках и не показывается повторно; пока он не задан
          здесь, используется значение из .env. SMTP-транспорт настраивается только в .env (S33).
          Сейчас:{" "}
          {notifications.telegramTokenSet
            ? `токен задан (${notifications.telegramTokenSource})`
            : "токен не задан"}
        </p>
        <Input
          label="Новый токен бота (оставьте пустым, чтобы не менять)"
          value={token}
          placeholder="123456789:AA…"
          onChange={(e) => setToken(e.target.value)}
        />
        <fieldset className={settingsStyles.rules}>
          <legend>Правила уведомлений (2.6)</legend>
          {Object.entries(NOTIFICATION_EVENTS).map(([event, label]) => (
            <label key={event} className={styles.checkbox}>
              <input
                type="checkbox"
                checked={rules[event] ?? true}
                onChange={(e) => setRules({ ...rules, [event]: e.target.checked })}
              />
              {label}
            </label>
          ))}
        </fieldset>
      </div>
      <Button
        variant="accent"
        loading={busy}
        onClick={() =>
          onSaved(
            () =>
              updateNotificationSettings({
                telegramBotToken: token.trim() === "" ? null : token.trim(),
                rules,
              }),
            "Настройки уведомлений сохранены",
          )
        }
      >
        Сохранить уведомления
      </Button>
    </Card>
  );
}

function StockSection({
  busy,
  settings,
  onSaved,
}: {
  busy: boolean;
  settings: SettingsResponse;
  onSaved: SavedHandler;
}) {
  const [form, setForm] = useState(settings.stock);
  useEffect(() => setForm(settings.stock), [settings.stock]);

  const options = settings.locations.map((l) => ({ value: l.id, label: l.name }));

  return (
    <Card title="Склад по умолчанию">
      <div className={settingsStyles.fields}>
        <p className={styles.hint}>
          С него резервируются заказы (1.6) и с ним сравнивается min_stock в дашборде.
        </p>
        <Select
          label="Склад"
          options={options}
          value={form.defaultLocationId || options[0]?.value || ""}
          onChange={(e) => setForm({ defaultLocationId: e.target.value })}
        />
      </div>
      <Button
        variant="accent"
        loading={busy}
        onClick={() => onSaved(() => updateStockSettings(form), "Склад по умолчанию сохранён")}
      >
        Сохранить склад
      </Button>
    </Card>
  );
}

function AuthSection({
  busy,
  settings,
  onSaved,
}: {
  busy: boolean;
  settings: SettingsResponse;
  onSaved: SavedHandler;
}) {
  const [form, setForm] = useState(settings.auth);
  useEffect(() => setForm(settings.auth), [settings.auth]);

  return (
    <Card title="Регистрация">
      <div className={settingsStyles.fields}>
        <p className={styles.hint}>
          Гейт подтверждения email (S08.1): саморегистрация физлиц требует перехода по ссылке из
          письма. Заведённые админом пользователи не блокируются.
        </p>
        <label className={styles.checkbox}>
          <input
            type="checkbox"
            checked={form.requireEmailConfirmation}
            onChange={(e) => setForm({ requireEmailConfirmation: e.target.checked })}
          />
          Требовать подтверждение email при саморегистрации
        </label>
      </div>
      <Button
        variant="accent"
        loading={busy}
        onClick={() => onSaved(() => updateAuthSettings(form), "Настройка регистрации сохранена")}
      >
        Сохранить
      </Button>
    </Card>
  );
}

function LandingSection({
  busy,
  settings,
  onSaved,
}: {
  busy: boolean;
  settings: SettingsResponse;
  onSaved: SavedHandler;
}) {
  const landing = settings.landing;
  const [hero, setHero] = useState({ eyebrow: "", title: "", text: "" });
  const [delivery, setDelivery] = useState({
    title: "",
    steps: [] as { number: string; title: string; text: string }[],
  });

  useEffect(() => {
    if (landing?.hero) setHero(landing.hero);
    if (landing?.delivery) setDelivery(landing.delivery);
  }, [landing]);

  const updateStep = (
    index: number,
    patch: Partial<{ number: string; title: string; text: string }>,
  ) => {
    setDelivery((prev) => ({
      ...prev,
      steps: prev.steps.map((step, i) => (i === index ? { ...step, ...patch } : step)),
    }));
  };

  return (
    <Card title="Витрина — тексты лендинга">
      <div className={settingsStyles.fields}>
        <p className={styles.hint}>
          Правка текстов главной страницы без коммита. Пустые поля — значения витрины по умолчанию.
        </p>
        <Input
          label="Надзаголовок"
          value={hero.eyebrow}
          onChange={(e) => setHero({ ...hero, eyebrow: e.target.value })}
        />
        <Input
          label="Заголовок"
          value={hero.title}
          onChange={(e) => setHero({ ...hero, title: e.target.value })}
        />
        <Input
          label="Подзаголовок"
          value={hero.text}
          onChange={(e) => setHero({ ...hero, text: e.target.value })}
        />
        <Input
          label="Блок «Как мы доставляем» — заголовок"
          value={delivery.title}
          onChange={(e) => setDelivery({ ...delivery, title: e.target.value })}
        />
        {delivery.steps.map((step, index) => (
          <div key={index} className={settingsStyles.step}>
            <Input
              label={`Шаг ${index + 1}: номер`}
              value={step.number}
              onChange={(e) => updateStep(index, { number: e.target.value })}
            />
            <Input
              label={`Шаг ${index + 1}: название`}
              value={step.title}
              onChange={(e) => updateStep(index, { title: e.target.value })}
            />
            <Input
              label={`Шаг ${index + 1}: текст`}
              value={step.text}
              onChange={(e) => updateStep(index, { text: e.target.value })}
            />
          </div>
        ))}
      </div>
      <Button
        variant="accent"
        loading={busy}
        onClick={() =>
          onSaved(
            () =>
              updateLandingSettings({
                // не затираем блок, который в этой форме не редактировался
                ...(hero.title.trim() || hero.eyebrow.trim() || hero.text.trim()
                  ? { hero }
                  : {}),
                ...(delivery.title.trim() || delivery.steps.length > 0
                  ? { delivery }
                  : {}),
              }),
            "Тексты лендинга сохранены",
          )
        }
      >
        Сохранить витрину
      </Button>
    </Card>
  );
}
