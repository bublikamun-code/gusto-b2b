/**
 * Обёртка чата (S34): сейчас — Crisp (free plan); позже заменяется своим
 * WebSocket-чатом без изменения вызовов в UI. Идентификатор сайта —
 * VITE_CRISP_WEBSITE_ID; без него чат не подключается.
 */
const WEBSITE_ID = import.meta.env.VITE_CRISP_WEBSITE_ID as string | undefined;

export function chatEnabled(): boolean {
  return Boolean(WEBSITE_ID);
}

export function initChat(): void {
  if (!chatEnabled() || document.getElementById("crisp-chat-script")) {
    return;
  }
  window.$crisp = window.$crisp ?? [];
  const script = document.createElement("script");
  script.id = "crisp-chat-script";
  script.src = "https://client.crisp.chat/l.js";
  script.async = true;
  document.head.appendChild(script);
}

export function openChat(): void {
  if (chatEnabled() && window.$crisp) {
    window.$crisp.push(["do", "chat:open"]);
  }
}

declare global {
  interface Window {
    $crisp?: unknown[];
  }
}
