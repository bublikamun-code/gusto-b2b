import { useCallback, useEffect, useState } from "react";
import { Badge, Button, Input, Table, Textarea, useToast } from "../../components/ui";
import {
  archiveArticle,
  createArticle,
  listAdminArticles,
  publishArticle,
  updateArticle,
  type AdminArticle,
} from "../../api/cms";
import styles from "./AdminCmsPage.module.scss";

/** Админка статей (S37): draft → publish, правка контента, архив. */
export default function AdminCmsPage() {
  const { push } = useToast();
  const [articles, setArticles] = useState<AdminArticle[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<AdminArticle | null>(null);
  const [creating, setCreating] = useState(false);
  const [slug, setSlug] = useState("");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    listAdminArticles()
      .then((page) => setArticles(page.items))
      .catch((err) => push((err as Error).message, "error"))
      .finally(() => setLoading(false));
  }, [push]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setCreating(true);
    setSlug("");
    setTitle("");
    setBody("");
  };

  const openEdit = (article: AdminArticle) => {
    setCreating(false);
    setEditing(article);
    setSlug(article.slug);
    setTitle(article.title);
    setBody(article.body);
  };

  const save = async () => {
    setBusy(true);
    try {
      if (creating) {
        await createArticle({ slug: slug.trim(), title: title.trim(), body });
        push("Статья создана (черновик)", "success");
      } else if (editing) {
        await updateArticle(editing.id, { title: title.trim(), body });
        push("Статья сохранена", "success");
      }
      setCreating(false);
      setEditing(null);
      load();
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  const publish = async (article: AdminArticle) => {
    try {
      await publishArticle(article.id);
      push(`«${article.title}» опубликована`, "success");
      load();
    } catch (err) {
      push((err as Error).message, "error");
    }
  };

  const archive = async (article: AdminArticle) => {
    try {
      await archiveArticle(article.id);
      push(`«${article.title}» в архиве`, "success");
      load();
    } catch (err) {
      push((err as Error).message, "error");
    }
  };

  const columns = [
    { key: "slug", title: "Slug", render: (a: AdminArticle) => <strong>/{a.slug}</strong> },
    { key: "title", title: "Заголовок", render: (a: AdminArticle) => a.title },
    {
      key: "status",
      title: "Статус",
      render: (a: AdminArticle) => (
        <Badge variant={a.status === "PUBLISHED" ? "success" : a.status === "ARCHIVED" ? "outline" : "neutral"}>
          {a.status === "PUBLISHED" ? "Опубликована" : a.status === "ARCHIVED" ? "Архив" : "Черновик"}
        </Badge>
      ),
    },
    {
      key: "actions",
      title: "",
      align: "right" as const,
      render: (a: AdminArticle) => (
        <div className={styles.rowActions}>
          <Button size="sm" variant="secondary" onClick={() => openEdit(a)}>
            Править
          </Button>
          {a.status !== "PUBLISHED" && (
            <Button size="sm" variant="accent" onClick={() => publish(a)}>
              Опубликовать
            </Button>
          )}
          {a.status === "PUBLISHED" && (
            <Button size="sm" variant="secondary" onClick={() => archive(a)}>
              В архив
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>Страницы сайта</h1>
        <Button variant="accent" onClick={openCreate}>
          Новая страница
        </Button>
      </header>

      <Table<AdminArticle>
        columns={columns}
        data={articles}
        rowKey={(a) => a.id}
        loading={loading}
        empty="Страниц пока нет — создайте «О нас» или «Доставка»"
      />

      {(creating || editing) && (
        <div className={styles.overlay} onClick={() => { setCreating(false); setEditing(null); }}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <h2>{creating ? "Новая страница" : `Правка: /${editing?.slug}`}</h2>
            {creating && (
              <Input label="Slug (адрес страницы)" value={slug} onChange={(e) => setSlug(e.target.value)} />
            )}
            <Input label="Заголовок" value={title} onChange={(e) => setTitle(e.target.value)} />
            <Textarea
              label="Текст (абзацы разделяются пустой строкой)"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              rows={10}
            />
            <div className={styles.modalActions}>
              <Button variant="secondary" onClick={() => { setCreating(false); setEditing(null); }}>
                Отмена
              </Button>
              <Button variant="accent" loading={busy} disabled={!title.trim() || !body.trim()} onClick={save}>
                Сохранить
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
