import type {
  Company,
  CompanyFilters,
  CreateCompanyRequest,
  CreateUserRequest,
  PagedResponse,
  ResetPasswordResult,
  UpdateCompanyRequest,
  UpdateUserRequest,
  User,
  UserFilters,
} from "../types/admin";

const USE_MOCK = import.meta.env.VITE_API_MOCK === "true";

let mockUsers: User[] = [
  {
    id: "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    email: "admin@gustomeat.by",
    fullName: "Администратор Густо",
    phone: "+375291234567",
    role: "ADMIN",
    companyId: null,
    isActive: true,
    totpEnabled: false,
    createdAt: "2026-08-20T09:00:00Z",
    updatedAt: "2026-08-20T09:00:00Z",
  },
  {
    id: "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    email: "manager@gustomeat.by",
    fullName: "Иванов Иван Иванович",
    phone: "+375297654321",
    role: "MANAGER",
    companyId: null,
    isActive: true,
    totpEnabled: false,
    createdAt: "2026-08-21T10:00:00Z",
    updatedAt: "2026-08-21T10:00:00Z",
  },
  {
    id: "c3d4e5f6-a7b8-9012-cdef-123456789012",
    email: "buh@gustomeat.by",
    fullName: "Петрова Мария Сергеевна",
    phone: null,
    role: "ACCOUNTANT",
    companyId: null,
    isActive: true,
    totpEnabled: false,
    createdAt: "2026-08-21T11:00:00Z",
    updatedAt: "2026-08-21T11:00:00Z",
  },
  {
    id: "d4e5f6a7-b8c9-0123-defa-234567890123",
    email: "client@tiopttrade.by",
    fullName: "Сидоров Алексей Петрович",
    phone: "+375291112233",
    role: "CUSTOMER_LEGAL",
    companyId: "e5f6a7b8-c9d0-1234-efab-345678901234",
    isActive: true,
    totpEnabled: false,
    createdAt: "2026-08-22T12:00:00Z",
    updatedAt: "2026-08-22T12:00:00Z",
  },
];

let mockCompanies: Company[] = [
  {
    id: "e5f6a7b8-c9d0-1234-efab-345678901234",
    name: 'ООО "ТиоптТрейд"',
    shortName: "ТиоптТрейд",
    unp: "123456789",
    legalAddress: "г. Минск, ул. Победителей, 1",
    actualAddress: "г. Минск, ул. Победителей, 1",
    bankAccount: "BY00UNBS12345678901234567890",
    bankName: 'ОАО "Беларусбанк"',
    bankBic: "UNBSBY2X",
    contactPhone: "+375291112233",
    contactEmail: "client@tiopttrade.by",
    status: "ACTIVE",
    managerId: "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    createdAt: "2026-08-22T12:00:00Z",
    updatedAt: "2026-08-22T12:00:00Z",
  },
  {
    id: "f6a7b8c9-d0e1-2345-fabc-456789012345",
    name: 'ИП "Мясной двор"',
    shortName: "Мясной двор",
    unp: "987654321",
    legalAddress: "г. Минск, пр. Независимости, 10",
    actualAddress: null,
    bankAccount: null,
    bankName: null,
    bankBic: null,
    contactPhone: "+375293332211",
    contactEmail: null,
    status: "INACTIVE",
    managerId: null,
    createdAt: "2026-08-23T13:00:00Z",
    updatedAt: "2026-08-23T13:00:00Z",
  },
];

function uuid(): string {
  return `${1e7}-${1e3}-${4e3}-${8e3}-${1e11}`.replace(/[018]/g, (c) =>
    (Number(c) ^ (crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (Number(c) / 4)))).toString(16),
  );
}

function now(): string {
  return new Date().toISOString();
}

function normalizeBoolean(value: boolean | "" | undefined): boolean | undefined {
  if (value === "") return undefined;
  return value;
}

export function isMockEnabled(): boolean {
  return USE_MOCK;
}

export function listMockUsers(filters: UserFilters): PagedResponse<User> {
  const page = filters.page ?? 0;
  const size = filters.size ?? 20;
  const activeFilter = normalizeBoolean(filters.isActive);

  let items = mockUsers.filter((u) => {
    if (filters.role && u.role !== filters.role) return false;
    if (filters.companyId && u.companyId !== filters.companyId) return false;
    if (activeFilter !== undefined && u.isActive !== activeFilter) return false;
    if (filters.search) {
      const q = filters.search.toLowerCase();
      const text = `${u.email} ${u.fullName} ${u.phone ?? ""}`.toLowerCase();
      if (!text.includes(q)) return false;
    }
    return true;
  });

  const total = items.length;
  items = items.slice(page * size, (page + 1) * size);
  return { items, page, size, total };
}

export function createMockUser(body: CreateUserRequest): User {
  const user: User = {
    id: uuid(),
    email: body.email,
    fullName: body.fullName,
    phone: body.phone ?? null,
    role: body.role,
    companyId: body.companyId ?? null,
    isActive: true,
    totpEnabled: false,
    createdAt: now(),
    updatedAt: now(),
  };
  mockUsers = [...mockUsers, user];
  return user;
}

export function getMockUser(id: string): User | undefined {
  return mockUsers.find((u) => u.id === id);
}

export function updateMockUser(id: string, body: UpdateUserRequest): User {
  const index = mockUsers.findIndex((u) => u.id === id);
  if (index === -1) throw new Error("NOT_FOUND");
  const existing = mockUsers[index];
  const updated: User = {
    ...existing,
    fullName: body.fullName ?? existing.fullName,
    phone: body.phone === undefined ? existing.phone : body.phone,
    role: body.role ?? existing.role,
    companyId: body.companyId === undefined ? existing.companyId : body.companyId,
    isActive: body.isActive ?? existing.isActive,
    updatedAt: now(),
  };
  mockUsers = [...mockUsers.slice(0, index), updated, ...mockUsers.slice(index + 1)];
  return updated;
}

export function deleteMockUser(id: string): void {
  const index = mockUsers.findIndex((u) => u.id === id);
  if (index === -1) throw new Error("NOT_FOUND");
  mockUsers = [...mockUsers.slice(0, index), ...mockUsers.slice(index + 1)];
}

export function resetMockPassword(id: string): ResetPasswordResult {
  const user = mockUsers.find((u) => u.id === id);
  if (!user) throw new Error("NOT_FOUND");
  const temporaryPassword = Math.random().toString(36).slice(2, 10) + Math.random().toString(36).slice(2, 4).toUpperCase();
  return { temporaryPassword };
}

export function listMockCompanies(filters: CompanyFilters): PagedResponse<Company> {
  const page = filters.page ?? 0;
  const size = filters.size ?? 20;

  let items = mockCompanies.filter((c) => {
    if (filters.status && c.status !== filters.status) return false;
    if (filters.search) {
      const q = filters.search.toLowerCase();
      const text = `${c.name} ${c.shortName ?? ""} ${c.unp}`.toLowerCase();
      if (!text.includes(q)) return false;
    }
    return true;
  });

  const total = items.length;
  items = items.slice(page * size, (page + 1) * size);
  return { items, page, size, total };
}

export function createMockCompany(body: CreateCompanyRequest): Company {
  const company: Company = {
    id: uuid(),
    name: body.name,
    shortName: body.shortName ?? null,
    unp: body.unp ?? null,
    legalAddress: body.legalAddress ?? null,
    actualAddress: body.actualAddress ?? null,
    bankAccount: body.bankAccount ?? null,
    bankName: body.bankName ?? null,
    bankBic: body.bankBic ?? null,
    contactPhone: body.contactPhone ?? null,
    contactEmail: body.contactEmail ?? null,
    status: body.status ?? "ACTIVE",
    managerId: body.managerId ?? null,
    createdAt: now(),
    updatedAt: now(),
  };
  mockCompanies = [...mockCompanies, company];
  return company;
}

export function getMockCompany(id: string): Company | undefined {
  return mockCompanies.find((c) => c.id === id);
}

export function updateMockCompany(id: string, body: UpdateCompanyRequest): Company {
  const index = mockCompanies.findIndex((c) => c.id === id);
  if (index === -1) throw new Error("NOT_FOUND");
  const existing = mockCompanies[index];
  const updated: Company = {
    ...existing,
    name: body.name ?? existing.name,
    shortName: body.shortName === undefined ? existing.shortName : body.shortName,
    unp: body.unp ?? existing.unp,
    legalAddress: body.legalAddress === undefined ? existing.legalAddress : body.legalAddress,
    actualAddress: body.actualAddress === undefined ? existing.actualAddress : body.actualAddress,
    bankAccount: body.bankAccount === undefined ? existing.bankAccount : body.bankAccount,
    bankName: body.bankName === undefined ? existing.bankName : body.bankName,
    bankBic: body.bankBic === undefined ? existing.bankBic : body.bankBic,
    contactPhone: body.contactPhone === undefined ? existing.contactPhone : body.contactPhone,
    contactEmail: body.contactEmail === undefined ? existing.contactEmail : body.contactEmail,
    status: body.status ?? existing.status,
    managerId: body.managerId === undefined ? existing.managerId : body.managerId,
    updatedAt: now(),
  };
  mockCompanies = [...mockCompanies.slice(0, index), updated, ...mockCompanies.slice(index + 1)];
  return updated;
}

export function deleteMockCompany(id: string): void {
  const index = mockCompanies.findIndex((c) => c.id === id);
  if (index === -1) throw new Error("NOT_FOUND");
  mockCompanies = [...mockCompanies.slice(0, index), ...mockCompanies.slice(index + 1)];
}
