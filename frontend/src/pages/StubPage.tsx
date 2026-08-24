import { useEffect, useState } from "react";

/**
 * Временная заглушка главной (бывший скелет S03).
 * Реальная главная — S14 по чек-листу.
 */
export default function StubPage() {
  const [apiStatus, setApiStatus] = useState<string>("проверяем…");

  useEffect(() => {
    fetch("/healthz")
      .then((response) => (response.ok ? response.json() : Promise.reject(response.status)))
      .then((body) => setApiStatus(body?.data?.status ?? "UP"))
      .catch(() => setApiStatus("недоступен"));
  }, []);

  return (
    <div className="page">
      <header className="header">
        <span className="logo">ГУСТО</span>
        <span className="header__sub">мясной гастроном</span>
      </header>

      <main className="card">
        <h1>Скелет проекта поднят</h1>
        <p>
          Backend: <strong>{apiStatus}</strong>
        </p>
        <p className="card__note">
          Дизайн-система доступна на странице <a href="/ui-kit">/ui-kit</a>. Реальные
          страницы — с S14 по чек-листу.
        </p>
      </main>

      <footer className="footer">gustomeat.by · B2B-портал</footer>
    </div>
  );
}
