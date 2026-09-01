import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Input, Modal, Pagination, Select, Table, useToast } from "../../components/ui";
import { createCompany, deleteCompany, listCompanies, updateCompany } from "../../api/companies";
import { listUsers } from "../../api/users";
import type { Company, CompanyStatus, User } from "../../types/admin";
import styles from "./AdminPages.module.scss";

const STATUSES: { value: CompanyStatus; label: string }[] = [
  { value: "ACTIVE", label: "Активна" },
  { value: "INACTIVE", label: "Неактивна" },
];

const statusLabel = (status: CompanyStatus) => STATUSES.find((s) => s.value === status)?.label ?? status;

const companySchema = z.object({
  name: z.string().min(2, "Название не может быть короче 2 символов"),
  shortName: z.string().optional(),
  unp: z.string().regex(/^\d{9}|\d{10}$/, "УНП должен состоять из 9 или 10 цифр"),
  legalAddress: z.string().optional(),
  actualAddress: z.string().optional(),
  bankAccount: z.string().optional(),
  bankName: z.string().optional(),
  bankBic: z.string().optional(),
  contactPhone: z.string().optional(),
  contactEmail: z.string().email("Введите корректный email").optional().or(z.literal("")),
  status: z.enum(["ACTIVE", "INACTIVE"]),
  managerId: z.string().optional(),
});

type CompanyForm = z.infer<typeof companySchema>;

interface CompanyFormModalProps {
  open: boolean;
  company?: Company | null;
  managers: User[];
  onClose: () => void;
  onSubmit: (values: CompanyForm) => void;
  isSubmitting: boolean;
}

function CompanyFormModal({ open, company, managers, onClose, onSubmit, isSubmitting }: CompanyFormModalProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CompanyForm>({
    resolver: zodResolver(companySchema),
    values: company
      ? {
          name: company.name,
          shortName: company.shortName ?? undefined,
          unp: company.unp ?? "",
          legalAddress: company.legalAddress ?? undefined,
          actualAddress: company.actualAddress ?? undefined,
          bankAccount: company.bankAccount ?? undefined,
          bankName: company.bankName ?? undefined,
          bankBic: company.bankBic ?? undefined,
          contactPhone: company.contactPhone ?? undefined,
          contactEmail: company.contactEmail ?? undefined,
          status: company.status,
          managerId: company.managerId ?? undefined,
        }
      : {
          name: "",
          shortName: "",
          unp: "",
          legalAddress: "",
          actualAddress: "",
          bankAccount: "",
          bankName: "",
          bankBic: "",
          contactPhone: "",
          contactEmail: "",
          status: "ACTIVE",
          managerId: "",
        },
  });

  return (
    <Modal
      open={open}
      title={company ? "Редактировать компанию" : "Новая компания"}
      onClose={onClose}
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Отмена
          </Button>
          <Button type="submit" form="company-form" loading={isSubmitting}>
            {company ? "Сохранить" : "Создать"}
          </Button>
        </>
      }
    >
      <form
        id="company-form"
        className={styles.form}
        onSubmit={handleSubmit((values) => {
          onSubmit(values);
          if (!company) reset();
        })}
      >
        <Input label="Название" error={errors.name?.message} {...register("name")} />
        <Input label="Сокращённое название" error={errors.shortName?.message} {...register("shortName")} />
        <Input label="УНП" error={errors.unp?.message} maxLength={9} {...register("unp")} />
        <Input label="Юридический адрес" error={errors.legalAddress?.message} {...register("legalAddress")} />
        <Input label="Фактический адрес" error={errors.actualAddress?.message} {...register("actualAddress")} />
        <Input label="Расчётный счёт" error={errors.bankAccount?.message} {...register("bankAccount")} />
        <Input label="Банк" error={errors.bankName?.message} {...register("bankName")} />
        <Input label="БИК" error={errors.bankBic?.message} {...register("bankBic")} />
        <Input label="Контактный телефон" error={errors.contactPhone?.message} {...register("contactPhone")} />
        <Input label="Контактный email" type="email" error={errors.contactEmail?.message} {...register("contactEmail")} />
        <Select label="Статус" options={STATUSES} error={errors.status?.message} {...register("status")} />
        <Select
          label="Закреплённый менеджер"
          placeholder="Без менеджера"
          options={managers.map((m) => ({ value: m.id, label: m.fullName }))}
          error={errors.managerId?.message}
          {...register("managerId")}
        />
      </form>
    </Modal>
  );
}

export default function AdminCompaniesPage() {
  const { push } = useToast();
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState({ search: "", status: "" as "" | CompanyStatus });
  const [page, setPage] = useState(1);
  const [editingCompany, setEditingCompany] = useState<Company | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);

  const { data: companiesData, isLoading: isCompaniesLoading } = useQuery({
    queryKey: ["admin", "companies", filters, page],
    queryFn: () =>
      listCompanies({
        page: page - 1,
        size: 20,
        search: filters.search || undefined,
        status: filters.status || undefined,
      }),
  });

  const { data: managersData } = useQuery({
    queryKey: ["admin", "users", "managers"],
    queryFn: () => listUsers({ role: "MANAGER", size: 1000 }),
  });

  const managersById = useMemo(() => {
    const map = new Map<string, User>();
    managersData?.items.forEach((u) => map.set(u.id, u));
    return map;
  }, [managersData]);

  const createMutation = useMutation({
    mutationFn: createCompany,
    onSuccess: () => {
      push("Компания создана", "success");
      queryClient.invalidateQueries({ queryKey: ["admin", "companies"] });
      setIsFormOpen(false);
    },
    onError: (err: { message?: string }) => push(err.message ?? "Не удалось создать компанию", "error"),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Parameters<typeof updateCompany>[1] }) => updateCompany(id, body),
    onSuccess: () => {
      push("Компания обновлена", "success");
      queryClient.invalidateQueries({ queryKey: ["admin", "companies"] });
      setEditingCompany(null);
    },
    onError: (err: { message?: string }) => push(err.message ?? "Не удалось обновить компанию", "error"),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteCompany,
    onSuccess: () => {
      push("Компания удалена", "success");
      queryClient.invalidateQueries({ queryKey: ["admin", "companies"] });
    },
    onError: (err: { message?: string }) => push(err.message ?? "Не удалось удалить компанию", "error"),
  });

  const handleFormSubmit = (values: CompanyForm) => {
    const body = {
      ...values,
      shortName: values.shortName || null,
      legalAddress: values.legalAddress || null,
      actualAddress: values.actualAddress || null,
      bankAccount: values.bankAccount || null,
      bankName: values.bankName || null,
      bankBic: values.bankBic || null,
      contactPhone: values.contactPhone || null,
      contactEmail: values.contactEmail || null,
      managerId: values.managerId || null,
    };
    if (editingCompany) {
      updateMutation.mutate({ id: editingCompany.id, body });
    } else {
      createMutation.mutate(body);
    }
  };

  const handleEdit = (company: Company) => {
    setEditingCompany(company);
    setIsFormOpen(true);
  };

  const handleCloseForm = () => {
    setIsFormOpen(false);
    setEditingCompany(null);
  };

  const handleDelete = (company: Company) => {
    if (confirm(`Удалить компанию ${company.name}?`)) {
      deleteMutation.mutate(company.id);
    }
  };

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Компании</h1>
        <Button onClick={() => setIsFormOpen(true)}>Добавить компанию</Button>
      </header>

      <div className={styles.filters}>
        <Input
          placeholder="Поиск по названию, УНП"
          value={filters.search}
          onChange={(e) => setFilters((f) => ({ ...f, search: e.target.value }))}
        />
        <Select
          placeholder="Все статусы"
          options={STATUSES}
          value={filters.status}
          onChange={(e) => setFilters((f) => ({ ...f, status: e.target.value as CompanyStatus | "" }))}
        />
      </div>

      <Table<Company>
        columns={[
          { key: "name", title: "Название" },
          { key: "shortName", title: "Сокращение" },
          { key: "unp", title: "УНП" },
          { key: "contactPhone", title: "Телефон" },
          { key: "contactEmail", title: "Email" },
          {
            key: "status",
            title: "Статус",
            render: (row) => statusLabel(row.status),
          },
          {
            key: "manager",
            title: "Менеджер",
            render: (row) => (row.managerId ? managersById.get(row.managerId)?.fullName ?? "—" : "—"),
          },
          {
            key: "actions",
            title: "Действия",
            render: (row) => (
              <div className={styles.actions}>
                <Button size="sm" variant="secondary" onClick={() => handleEdit(row)}>
                  Изменить
                </Button>
                <Button size="sm" variant="secondary" onClick={() => handleDelete(row)}>
                  Удалить
                </Button>
              </div>
            ),
          },
        ]}
        data={companiesData?.items ?? []}
        rowKey={(row) => row.id}
        loading={isCompaniesLoading}
      />

      {companiesData && companiesData.total > 0 && (
        <div className={styles.pagination}>
          <Pagination page={page} size={20} total={companiesData.total} onChange={setPage} />
        </div>
      )}

      <CompanyFormModal
        open={isFormOpen}
        company={editingCompany}
        managers={managersData?.items ?? []}
        onClose={handleCloseForm}
        onSubmit={handleFormSubmit}
        isSubmitting={createMutation.isPending || updateMutation.isPending}
      />
    </div>
  );
}
