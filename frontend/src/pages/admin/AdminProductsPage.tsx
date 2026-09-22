import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Badge,
  Button,
  ConfirmModal,
  Input,
  Modal,
  Pagination,
  Table,
  useToast,
} from '../../components/ui';
import { listAdminProducts, updateShowcaseFlags, type AdminProduct } from '../../api/adminCatalog';
import { listBrands, listCategories } from '../../api/catalog';
import {
  deleteProductImage,
  listProductImages,
  uploadProductImage,
  type ProductImage,
} from '../../api/productImages';
import styles from './AdminPages.module.scss';

const PAGE_SIZE = 20;

interface ImageManagerModalProps {
  product: AdminProduct | null;
  open: boolean;
  onClose: () => void;
}

function ImageManagerModal({ product, open, onClose }: ImageManagerModalProps) {
  const { push } = useToast();
  const queryClient = useQueryClient();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [deletingImage, setDeletingImage] = useState<ProductImage | null>(null);

  const productId = product?.id;

  const { data: images = [], isLoading } = useQuery({
    queryKey: ['admin', 'product-images', productId],
    queryFn: () => listProductImages(productId!),
    enabled: Boolean(productId),
  });

  const uploadMutation = useMutation({
    mutationFn: () => uploadProductImage(productId!, selectedFile!),
    onSuccess: () => {
      push('Фото загружено', 'success');
      setSelectedFile(null);
      queryClient.invalidateQueries({ queryKey: ['admin', 'product-images', productId] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
    },
    onError: (err: { message?: string }) =>
      push(err.message ?? 'Не удалось загрузить фото', 'error'),
  });

  const deleteMutation = useMutation({
    mutationFn: (imageId: string) => deleteProductImage(productId!, imageId),
    onSuccess: () => {
      push('Фото удалено', 'success');
      setDeletingImage(null);
      queryClient.invalidateQueries({ queryKey: ['admin', 'product-images', productId] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
    },
    onError: (err: { message?: string }) => push(err.message ?? 'Не удалось удалить фото', 'error'),
  });

  function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null;
    setSelectedFile(file);
  }

  function handleUpload() {
    if (!selectedFile) {
      push('Выберите файл', 'error');
      return;
    }
    uploadMutation.mutate();
  }

  return (
    <Modal
      open={open}
      title={product ? `Фото: ${product.name}` : 'Фото товара'}
      onClose={onClose}
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Закрыть
          </Button>
          <Button
            type="button"
            loading={uploadMutation.isPending}
            onClick={handleUpload}
            disabled={!selectedFile}
          >
            Загрузить
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        <Input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          onChange={handleFileChange}
          label="Выберите изображение"
        />

        {isLoading && <p className={styles.hint}>Загрузка списка фото…</p>}

        {!isLoading && images.length === 0 && (
          <p className={styles.hint}>Пока нет загруженных фото.</p>
        )}

        <div className={styles.imageGrid}>
          {images.map((image) => (
            <div key={image.id} className={styles.imageItem}>
              <img src={image.url} alt="" className={styles.imageThumb} />
              <Button size="sm" variant="secondary" onClick={() => setDeletingImage(image)}>
                Удалить
              </Button>
            </div>
          ))}
        </div>
      </div>

      <ConfirmModal
        open={deletingImage !== null}
        title="Удалить фото"
        confirmLabel="Удалить"
        loading={deleteMutation.isPending}
        onClose={() => setDeletingImage(null)}
        onConfirm={() => productId && deletingImage && deleteMutation.mutate(deletingImage.id)}
      >
        <p>Удалить фото товара «{product?.name}»? Действие необратимо.</p>
      </ConfirmModal>
    </Modal>
  );
}

export default function AdminProductsPage() {
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [managingProduct, setManagingProduct] = useState<AdminProduct | null>(null);
  const queryClient = useQueryClient();
  const { push } = useToast();

  const { data: productsData, isLoading } = useQuery({
    queryKey: ['admin', 'products', { page: page - 1, search }],
    queryFn: () =>
      listAdminProducts({ page: page - 1, size: PAGE_SIZE, search: search || undefined }),
  });

  const showcaseMutation = useMutation({
    mutationFn: ({ id, isHit, isNew }: { id: string; isHit: boolean; isNew: boolean }) =>
      updateShowcaseFlags(id, { isHit, isNew }),
    onSuccess: (product) => {
      push(`Витрина обновлена: ${product.name}`, 'success');
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
    },
    onError: (err: { message?: string }) =>
      push(err.message ?? 'Не удалось обновить витрину', 'error'),
  });

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: listCategories,
  });

  const { data: brands = [] } = useQuery({
    queryKey: ['brands'],
    queryFn: listBrands,
  });

  const categoriesById = useMemo(() => {
    const map = new Map<string, string>();
    categories.forEach((c) => map.set(c.id, c.name));
    return map;
  }, [categories]);

  const brandsById = useMemo(() => {
    const map = new Map<string, string>();
    brands.forEach((b) => map.set(b.id, b.name));
    return map;
  }, [brands]);

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Товары</h1>
      </header>

      <div className={styles.filters}>
        <Input
          placeholder="Поиск по названию, SKU"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(1);
          }}
        />
      </div>

      <Table<AdminProduct>
        columns={[
          { key: 'sku', title: 'Артикул' },
          { key: 'name', title: 'Название' },
          {
            key: 'category',
            title: 'Категория',
            render: (row) => (row.categoryId ? (categoriesById.get(row.categoryId) ?? '—') : '—'),
          },
          {
            key: 'brand',
            title: 'Бренд',
            render: (row) => (row.brandId ? (brandsById.get(row.brandId) ?? '—') : '—'),
          },
          { key: 'unit', title: 'Ед. изм.' },
          {
            key: 'isActive',
            title: 'Активен',
            render: (row) => (row.isActive ? 'Да' : 'Нет'),
          },
          {
            // Витрина (S19.1): клик по бейджу переключает флаг точечным PATCH
            key: 'showcase',
            title: 'Витрина',
            render: (row) => (
              <div style={{ display: 'flex', gap: '0.3rem' }}>
                {/* plan-03: до ответа тогглы строки заблокированы — повторный клик уйдёт с устаревшим row */}
                <button
                  type="button"
                  className={styles.toggle}
                  aria-pressed={row.isHit}
                  title="Показывать как ХИТ на витрине"
                  disabled={showcaseMutation.isPending && showcaseMutation.variables?.id === row.id}
                  onClick={() =>
                    showcaseMutation.mutate({ id: row.id, isHit: !row.isHit, isNew: row.isNew })
                  }
                >
                  <Badge variant={row.isHit ? 'accent' : 'outline'}>ХИТ</Badge>
                </button>
                <button
                  type="button"
                  className={styles.toggle}
                  aria-pressed={row.isNew}
                  title="Показывать как НОВИНКА на витрине"
                  disabled={showcaseMutation.isPending && showcaseMutation.variables?.id === row.id}
                  onClick={() =>
                    showcaseMutation.mutate({ id: row.id, isHit: row.isHit, isNew: !row.isNew })
                  }
                >
                  <Badge variant={row.isNew ? 'success' : 'outline'}>НОВИНКА</Badge>
                </button>
              </div>
            ),
          },
          {
            key: 'image',
            title: 'Фото',
            render: (row) =>
              row.imageUrl ? <img src={row.imageUrl} alt="" className={styles.inlineThumb} /> : '—',
          },
          {
            key: 'actions',
            title: 'Действия',
            render: (row) => (
              <Button size="sm" variant="secondary" onClick={() => setManagingProduct(row)}>
                Фото
              </Button>
            ),
          },
        ]}
        data={productsData?.items ?? []}
        rowKey={(row) => row.id}
        loading={isLoading}
      />

      {productsData && productsData.total > 0 && (
        <div className={styles.pagination}>
          <Pagination page={page} size={PAGE_SIZE} total={productsData.total} onChange={setPage} />
        </div>
      )}

      <ImageManagerModal
        product={managingProduct}
        open={managingProduct !== null}
        onClose={() => setManagingProduct(null)}
      />
    </div>
  );
}
