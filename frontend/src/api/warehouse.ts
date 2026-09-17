import { apiRequest, apiListRequest } from "./client";

export type WarehouseDocumentType = "INCOMING" | "OUTGOING" | "TRANSFER" | "WRITE_OFF" | "INVENTORY";
export type WarehouseDocumentStatus = "DRAFT" | "CONFIRMED" | "CANCELLED";
export type PurchaseOrderStatus = "DRAFT" | "SENT" | "PARTIAL" | "RECEIVED" | "CANCELLED";

export interface StockLocation {
  id: string;
  name: string;
  address?: string | null;
  isActive: boolean;
}

export interface BalanceRow {
  productId: string;
  sku: string;
  productName: string;
  locationId: string;
  locationName: string;
  quantity: number;
  reserved: number;
  available: number;
  minStock: number;
}

export interface DocumentItem {
  productId: string;
  sku?: string | null;
  productName?: string | null;
  quantity: number;
  price?: number | null;
}

export interface WarehouseDocument {
  id: string;
  number: string;
  type: WarehouseDocumentType;
  status: WarehouseDocumentStatus;
  locationFromId?: string | null;
  locationToId?: string | null;
  supplierId?: string | null;
  documentDate: string;
  note?: string | null;
  confirmedAt?: string | null;
  items: DocumentItem[];
}

export interface Supplier {
  id: string;
  name: string;
  unp?: string | null;
  phone?: string | null;
  email?: string | null;
  contactPerson?: string | null;
  note?: string | null;
  isActive: boolean;
}

export interface PurchaseOrderItem {
  productId: string;
  sku?: string | null;
  productName?: string | null;
  quantity: number;
  purchasePrice: number;
  receivedQuantity: number;
}

export interface PurchaseOrder {
  id: string;
  number: string;
  supplierId: string;
  supplierName?: string;
  status: PurchaseOrderStatus;
  expectedDate?: string | null;
  totalAmount: number;
  note?: string | null;
  items: PurchaseOrderItem[];
}

export interface ToOrderRow {
  productId: string;
  sku: string;
  productName: string;
  minStock: number;
  available: number;
}

export interface MovementRow {
  productId: string;
  sku: string;
  productName: string;
  locationName: string;
  type: string;
  quantity: number;
  referenceType?: string | null;
  note?: string | null;
  createdAt: string;
}

export interface CatalogProductRef {
  id: string;
  sku: string;
  name: string;
  unit: string;
}

const qs = (params: Record<string, string | number | undefined | null>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : "";
};

export const listLocations = () => apiRequest<StockLocation[]>("/warehouse/locations");

export const getBalanceReport = (locationId?: string, search?: string) =>
  apiRequest<BalanceRow[]>(`/warehouse/reports/balance${qs({ locationId, search })}`);

export const getToOrderReport = () => apiRequest<ToOrderRow[]>("/warehouse/reports/to-order");

export const getMovementsReport = (from: string, to: string, locationId?: string) =>
  apiRequest<MovementRow[]>(`/warehouse/reports/movements${qs({ from, to, locationId, limit: 200 })}`);

export const listWarehouseDocuments = (filters: { type?: string; status?: string; page?: number }) =>
  apiListRequest<WarehouseDocument>(`/warehouse/documents${qs(filters)}`);

export const getWarehouseDocument = (id: string) =>
  apiRequest<WarehouseDocument>(`/warehouse/documents/${id}`);

export const createWarehouseDocument = (body: unknown) =>
  apiRequest<WarehouseDocument>("/warehouse/documents", { method: "POST", body });

export const confirmWarehouseDocument = (id: string) =>
  apiRequest<WarehouseDocument>(`/warehouse/documents/${id}/confirm`, { method: "POST", body: {} });

export const cancelWarehouseDocument = (id: string) =>
  apiRequest<WarehouseDocument>(`/warehouse/documents/${id}/cancel`, { method: "POST", body: {} });

export const listSuppliers = (search?: string, page = 0) =>
  apiListRequest<Supplier>(`/warehouse/suppliers${qs({ search, page, size: 20 })}`);

export const createSupplier = (body: unknown) =>
  apiRequest<Supplier>("/warehouse/suppliers", { method: "POST", body });

export const updateSupplier = (id: string, body: unknown) =>
  apiRequest<Supplier>(`/warehouse/suppliers/${id}`, { method: "PUT", body });

export const deactivateSupplier = (id: string) =>
  apiRequest<void>(`/warehouse/suppliers/${id}`, { method: "DELETE" });

export const listPurchaseOrders = (filters: { status?: string; page?: number }) =>
  apiListRequest<PurchaseOrder>(`/warehouse/purchase-orders${qs(filters)}`);

export const createPurchaseOrder = (body: unknown) =>
  apiRequest<PurchaseOrder>("/warehouse/purchase-orders", { method: "POST", body });

export const sendPurchaseOrder = (id: string) =>
  apiRequest<PurchaseOrder>(`/warehouse/purchase-orders/${id}/send`, { method: "POST", body: {} });

export const cancelPurchaseOrder = (id: string) =>
  apiRequest<PurchaseOrder>(`/warehouse/purchase-orders/${id}/cancel`, { method: "POST", body: {} });

/** Компактный список товаров для выбора позиций в документах (публичный каталог). */
export const listProductRefs = (search?: string) =>
  apiListRequest<CatalogProductRef>(`/catalog/products${qs({ search, size: 50 })}`);
