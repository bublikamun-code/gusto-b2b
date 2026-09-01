import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Input, Modal, Pagination, Table, useToast } from "../../components/ui";
import { listAdminProducts, type AdminProduct } from "../../api/adminCatalog";
import { listBrands, listCategories } from "../../api/catalog";
import { deleteProductImage, listProductImages, uploadProductImage } from "../../api/productImages";
import styles from "./AdminPages.module.scss";

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

  const productId = product?.id;

  const { data: images = [], isLoading } = useQuery({
    queryKey: ["admin", "product-images", productId],
    queryFn: () => listProductImages(productId!),
    enabled: Boolean(productId),
  });

  const uploadMutation = useMutation({
    mutationFn: () => uploadProductImage(productId!, selectedFile!),
    onSuccess: () => {
      push("Фото загружено", "success");
      setSelectedFile(null);
      queryClient.invalidateQueries({ queryKey: ["admin", "product-images", productId] });
      queryClient.invalidateQueries({ queryKey: ["admin", "products"] });
    },
    onError: (err: { message?: string }) => push(err.message ?? "Не удалось загрузить фото", "error"),
  });

  const deleteMutation = useMutation({
    mutationFn: (imageId: string) => deleteProductImage(productId!, imageId),
    onSuccess: () => {
      push("Фото удалено", "success");
      queryClient.invalidateQueries({ queryKey: ["admin", "product-images", productId] });
      queryClient.invalidateQueries({ queryKey: ["admin", "products"] });
    },
    onError: (err: { message?: string }) => push(err.message ?? "Не удалось удалить фото", "error"),
  });

  function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null;
    setSelectedFile(file);
  }

  function handleUpload() {
    if (!selectedFile) {
      push("Выберите файл", "error");
      return;
    }
    uploadMutation.mutate();
  }

  return (
    <Modal
      open={open}
      title={product ? `Фото: ${product.name}` : "Фото товара"}
      onClose={onClose}
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Закрыть
          </Button>
          <Button type="button" loading={uploadMutation.isPending} onClick={handleUpload} disabled={!selectedFile}>
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

        {!isLoading && images.length === 0 && <p className={styles.hint}>Пока нет загруженных фото.</p>}

        <div className={styles.imageGrid}>
          {images.map((image) => (
            <div key={image.id} className={styles.imageItem}>
              <img src={image.url} alt="" className={styles.imageThumb} />
              <Button
                size="sm"
                variant="secondary"
                loading={deleteMutation.isPending && deleteMutation.variables === image.id}
                onClick={() => deleteMutation.mutate(image.id)}
              >
                Удалить
              </Button>
            </div>
          ))}
        </div>
      </div>
    </Modal>
  );
}

export default function AdminProductsPage() {
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState("");
  const [managingProduct, setManagingProduct] = useState<AdminProduct | null>(null);

  const { data: productsData, isLoading } = useQuery({
    queryKey: ["admin", "products", { page: page - 1, search }],
    queryFn: () => listAdminProducts({ page: page - 1, size: PAGE_SIZE, search: search || undefined }),
  });

  const { data: categories = [] } = useQuery({
    queryKey: ["categories"],
    queryFn: listCategories,
  });

  const { data: brands = [] } = useQuery({
    queryKey: ["brands"],
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
          { key: "sku", title: "Артикул" },
          { key: "name", title: "Название" },
          {
            key: "category",
            title: "Категория",
            render: (row) => (row.categoryId ? categoriesById.get(row.categoryId) ?? "—" : "—"),
          },
          {
            key: "brand",
            title: "Бренд",
            render: (row) => (row.brandId ? brandsById.get(row.brandId) ?? "—" : "—"),
          },
          { key: "unit", title: "Ед. изм." },
          {
            key: "isActive",
            title: "Активен",
            render: (row) => (row.isActive ? "Да" : "Нет"),
          },
          {
            key: "image",
            title: "Фото",
            render: (row) => (row.imageUrl ? <img src={row.imageUrl} alt="" className={styles.inlineThumb} /> : "—"),
          },
          {
            key: "actions",
            title: "Действия",
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
