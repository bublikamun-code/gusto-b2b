import { apiRequest } from "./client";
import type { components } from "./schema";

export type ProductImage = components["schemas"]["ProductImage"];

export function listProductImages(productId: string): Promise<ProductImage[]> {
  return apiRequest<ProductImage[]>(`/admin/catalog/products/${encodeURIComponent(productId)}/images`);
}

export function uploadProductImage(productId: string, file: File): Promise<ProductImage> {
  const formData = new FormData();
  formData.set("file", file);
  return apiRequest<ProductImage>(`/admin/catalog/products/${encodeURIComponent(productId)}/images`, {
    method: "POST",
    body: formData,
  });
}

export function deleteProductImage(productId: string, imageId: string): Promise<void> {
  return apiRequest<void>(
    `/admin/catalog/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}`,
    { method: "DELETE" },
  );
}
