import { useEffect, useState } from "react";
import { Button, Card, Input, useToast } from "../../components/ui";
import { apiRequest } from "../../api/client";
import { useAuthStore } from "../../store/authStore";
import styles from "./CabinetProfilePage.module.scss";

interface Profile {
  email: string;
  fullName: string;
  phone?: string | null;
  role: string;
}

export default function CabinetProfilePage() {
  const { push } = useToast();
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [saving, setSaving] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [changing, setChanging] = useState(false);

  useEffect(() => {
    apiRequest<Profile>("/cabinet/profile")
      .then((p) => {
        setProfile(p);
        setFullName(p.fullName);
        setPhone(p.phone ?? "");
      })
      .catch((err) => push((err as Error).message, "error"));
  }, [push]);

  const saveProfile = async () => {
    setSaving(true);
    try {
      const updated = await apiRequest<Profile>("/cabinet/profile", {
        method: "PATCH",
        body: { fullName, phone },
      });
      setProfile(updated);
      push("Профиль сохранён", "success");
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setSaving(false);
    }
  };

  const changePassword = async () => {
    if (newPassword.length < 8) {
      push("Новый пароль — минимум 8 символов", "error");
      return;
    }
    setChanging(true);
    try {
      await apiRequest("/cabinet/profile/password", {
        method: "POST",
        body: { currentPassword, newPassword },
      });
      push("Пароль изменён. Войдите с новым паролем.", "success");
      // Все сессии завершены — разлогиниваем
      setTimeout(() => {
        clearAuth();
        window.location.href = "/login";
      }, 1500);
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setChanging(false);
    }
  };

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Профиль</h1>

      <Card className={styles.card}>
        <h2>Данные</h2>
        <p className={styles.email}>{profile?.email}</p>
        <Input label="Имя и фамилия" value={fullName} onChange={(e) => setFullName(e.target.value)} />
        <Input label="Телефон" value={phone} onChange={(e) => setPhone(e.target.value)} />
        <Button onClick={saveProfile} loading={saving}>
          Сохранить
        </Button>
      </Card>

      <Card className={styles.card}>
        <h2>Смена пароля</h2>
        <Input
          label="Текущий пароль"
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
        />
        <Input
          label="Новый пароль"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />
        <p className={styles.hint}>После смены пароля потребуется войти заново.</p>
        <Button
          variant="secondary"
          onClick={changePassword}
          loading={changing}
          disabled={!currentPassword || !newPassword}
        >
          Изменить пароль
        </Button>
      </Card>
    </div>
  );
}
