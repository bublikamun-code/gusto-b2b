function upsertMeta(name: string, content: string, attribute: "name" | "property") {
  let meta = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${name}"]`);
  if (!meta) {
    meta = document.createElement("meta");
    meta.setAttribute(attribute, name);
    document.head.appendChild(meta);
  }
  meta.setAttribute("content", content);
}

export interface SeoParams {
  title: string;
  description: string;
}

/** Базовое SEO витрины (S21): title + description + Open Graph. */
export function applySeo({ title, description }: SeoParams) {
  document.title = title;
  upsertMeta("description", description, "name");
  upsertMeta("og:title", title, "property");
  upsertMeta("og:description", description, "property");
  upsertMeta("og:type", "website", "property");
}
