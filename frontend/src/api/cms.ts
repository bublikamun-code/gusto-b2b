import { apiListRequest, apiRequest } from "./client";

/** CMS (S37): публичные страницы и админка статей. */

export interface CmsPage {
  slug: string;
  title: string;
  body: string;
  publishedAt: string;
}

export interface AdminArticle {
  id: string;
  slug: string;
  title: string;
  body: string;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  publishedAt?: string | null;
}

export function getPublicPage(slug: string) {
  return apiRequest<CmsPage>(`/cms/pages/${encodeURIComponent(slug)}`);
}

export function listAdminArticles() {
  return apiListRequest<AdminArticle>("/admin/cms/articles?size=100");
}

export function createArticle(body: { slug: string; title: string; body: string }) {
  return apiRequest<AdminArticle>("/admin/cms/articles", { method: "POST", body });
}

export function updateArticle(id: string, body: { title?: string; body?: string }) {
  return apiRequest<AdminArticle>(`/admin/cms/articles/${id}`, { method: "PUT", body });
}

export function publishArticle(id: string) {
  return apiRequest<AdminArticle>(`/admin/cms/articles/${id}/publish`, { method: "POST" });
}

export function archiveArticle(id: string) {
  return apiRequest<AdminArticle>(`/admin/cms/articles/${id}`, { method: "DELETE" });
}
