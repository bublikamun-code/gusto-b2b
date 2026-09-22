import { useCallback, useEffect, useState } from 'react';
import { Badge, Button, ConfirmModal, Input, Modal, Table, Textarea, useToast } from '../../components/ui';
import {
  archiveArticle,
  createArticle,
  listAdminArticles,
  publishArticle,
  updateArticle,
  type AdminArticle,
} from '../../api/cms';
import styles from './AdminCmsPage.module.scss';

/** Админка статей (S37): draft → publish, правка контента, архив. */
export default function AdminCmsPage() {
  const { push } = useToast();
  const [articles, setArticles] = useState<AdminArticle[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<AdminArticle | null>(null);
  const [creating, setCreating] = useState(false);
  const [slug, setSlug] = useState('');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [busy, setBusy] = useState(false);
  const [acting, setActing] = useState(false);
  const [pendingAction, setPendingAction] = useState<{
    action: 'publish' | 'archive';
    article: AdminArticle;
  } | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    listAdminArticles()
      .then((page) => setArticles(page.items))
      .catch((err) => push((err as Error).message, 'error'))
      .finally(() => setLoading(false));
  }, [push]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setCreating(true);
    setSlug('');
    setTitle('');
    setBody('');
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
        push('Статья создана (черновик)', 'success');
      } else if (editing) {
        await updateArticle(editing.id, { title: title.trim(), body });
        push('Статья сохранена', 'success');
      }
      setCreating(false);
      setEditing(null);
      load();
    } catch (err) {
      push((err as Error).message, 'error');
    } finally {
      setBusy(false);
    }
  };

  const runAction = async () => {
    if (!pendingAction || acting) return;
    const { action, article } = pendingAction;
    setActing(true);
    try {
      if (action === 'publish') {
        await publishArticle(article.id);
        push(`«${article.title}» опубликована`, 'success');
      } else {
        await archiveArticle(article.id);
        push(`«${article.title}» в архиве`, 'success');
      }
      setPendingAction(null);
      load();
    } catch (err) {
      push((err as Error).message, 'error');
    } finally {
      setActing(false);
    }
  };

  const columns = [
    { key: 'slug', title: 'Slug', render: (a: AdminArticle) => <strong>/{a.slug}</strong> },
    { key: 'title', title: 'Заголовок', render: (a: AdminArticle) => a.title },
    {
      key: 'status',
      title: 'Статус',
      render: (a: AdminArticle) => (
        <Badge
          variant={
            a.status === 'PUBLISHED' ? 'success' : a.status === 'ARCHIVED' ? 'outline' : 'neutral'
          }
        >
          {a.status === 'PUBLISHED'
            ? 'Опубликована'
            : a.status === 'ARCHIVED'
              ? 'Архив'
              : 'Черновик'}
        </Badge>
      ),
    },
    {
      key: 'actions',
      title: '',
      align: 'right' as const,
      render: (a: AdminArticle) => (
        <div className={styles.rowActions}>
          <Button size="sm" variant="secondary" onClick={() => openEdit(a)}>
            Править
          </Button>
          {a.status !== 'PUBLISHED' && (
            <Button
              size="sm"
              variant="accent"
              onClick={() => setPendingAction({ action: 'publish', article: a })}
            >
              Опубликовать
            </Button>
          )}
          {a.status === 'PUBLISHED' && (
            <Button
              size="sm"
              variant="secondary"
              onClick={() => setPendingAction({ action: 'archive', article: a })}
            >
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

      <Modal
        open={!!(creating || editing)}
        onClose={() => {
          setCreating(false);
          setEditing(null);
        }}
        title={creating ? 'Новая страница' : editing ? `Правка: /${editing.slug}` : undefined}
      >
        {creating && (
          <Input
            label="Slug (адрес страницы)"
            value={slug}
            onChange={(e) => setSlug(e.target.value)}
          />
        )}
        <Input label="Заголовок" value={title} onChange={(e) => setTitle(e.target.value)} />
        <Textarea
          label="Текст (абзацы разделяются пустой строкой)"
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={10}
        />
        <div className={styles.modalActions}>
          <Button
            variant="secondary"
            onClick={() => {
              setCreating(false);
              setEditing(null);
            }}
          >
            Отмена
          </Button>
          <Button
            variant="accent"
            loading={busy}
            disabled={!title.trim() || !body.trim()}
            onClick={save}
          >
            Сохранить
          </Button>
        </div>
      </Modal>

      <ConfirmModal
        open={pendingAction !== null}
        title={pendingAction?.action === 'publish' ? 'Опубликовать страницу' : 'В архив'}
        confirmLabel={pendingAction?.action === 'publish' ? 'Опубликовать' : 'В архив'}
        loading={acting}
        onClose={() => setPendingAction(null)}
        onConfirm={runAction}
      >
        <p>
          {pendingAction?.action === 'publish'
            ? `Опубликовать «${pendingAction.article.title}»? Страница станет доступна всем посетителям сайта.`
            : `Убрать «${pendingAction?.article.title}» с сайта в архив? Страница перестанет открываться по своему адресу.`}
        </p>
      </ConfirmModal>
    </div>
  );
}
