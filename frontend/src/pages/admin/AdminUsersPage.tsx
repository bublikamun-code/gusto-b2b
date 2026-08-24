import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Input, Modal, Pagination, Select, Table, useToast } from "../../components/ui";
import { createUser, deleteUser, listUsers, resetUserPassword, updateUser } from "../../api/users";
import { listCompanies } from "../../api/companies";
import type { Company, Role, User } from "../../types/admin";
import styles from "./AdminPages.module.scss";

const ROLES: { value: Role; label: string }[] = [
  { value: "ADMIN", label: "Администратор" },
  { value: "ACCOUNTANT", label: "Бухгалтер" },
  { value: "MANAGER", label: "Менеджер" },
  { value: "CUSTOMER_LEGAL", label: "Клиент (юрлицо)" },
  { value: "CUSTOMER_INDIVIDUAL", label: "Клиент (физлицо)" },
];

const roleLabel = (role: Role) => ROLES.find((r) => r.value === role)?.label ?? role;

const userSchema = z.object({
  email: z.string().email("Введите корректный email"),
  fullName: z.string().min(2, "ФИО не может быть короче 2 символов"),
  phone: z.string().optional(),
  role: z.enum(["ADMIN", "ACCOUNTANT", "MANAGER", "CUSTOMER_LEGAL", "CUSTOMER_INDIVIDUAL"]),
  companyId: z.string().optional(),
  isActive: z.boolean(),
  password: z.string().min(8, "Пароль не может быть короче 8 символов").optional(),
});

type UserForm = z.infer<typeof userSchema>;

interface UserFormModalProps {
  open: boolean;
  user?: User | null;
  companies: Company[];
  onClose: () => void;
  onSubmit: (values: UserForm) => void;
  isSubmitting: boolean;
}

function UserFormModal({ open, user, companies, onClose, onSubmit, isSubmitting }: UserFormModalProps) {
  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<UserForm>({
    resolver: zodResolver(userSchema),
    values: user
      ? {
          email: user.email,
          fullName: user.fullName,
          phone: user.phone ?? undefined,
          role: user.role,
          companyId: user.companyId ?? undefined,
          isActive: user.isActive,
          password: undefined,
        }
      : {
          email: "",
          fullName: "",
          phone: "",
          role: "CUSTOMER_LEGAL",
          companyId: "",
          isActive: true,
          password: "",
        },
  });

  const roleValue = watch("role");
  const showCompany = roleValue === "CUSTOMER_LEGAL";

  return (
    <Modal
      open={open}
      title={user ? "Редактировать пользователя" : "Новый пользователь"}
      onClose={onClose}
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Отмена
          </Button>
          <Button type="submit" form="user-form" loading={isSubmitting}>
            {user ? "Сохранить" : "Создать"}
          </Button>
        </>
      }
    >
      <form
        id="user-form"
        className={styles.form}
        onSubmit={handleSubmit((values) => {
          onSubmit(values);
          if (!user) reset();
        })}
      >
        <Input label="Email" type="email" error={errors.email?.message} {...register("email")} />
        <Input label="ФИО" error={errors.fullName?.message} {...register("fullName")} />
        <Input label="Телефон" error={errors.phone?.message} {...register("phone")} />
        <Select label="Роль" options={ROLES} value={watch("role")} error={errors.role?.message} {...register("role")} />
        {showCompany && (
          <Select
            label="Компания"
            placeholder="Выберите компанию"
            options={companies.map((c) => ({ value: c.id, label: `${c.name} (УНП ${c.unp})` }))}
            value={watch("companyId") ?? ""}
            error={errors.companyId?.message}
            {...register("companyId")}
          />
        )}
        {!user && (
          <Input
            label="Пароль (необязательно)"
            type="password"
            placeholder="Будет сгенерирован временный пароль"
            error={errors.password?.message}
            {...register("password")}
          />
        )}
        <label className={styles.checkbox}>
          <input type="checkbox" {...register("isActive")} />
          <span>Активен</span>
        </label>
      </form>
    </Modal>
  );
}

export default function AdminUsersPage() {
  const { push } = useToast();
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState({ search: "", role: "", isActive: "" as "" | "true" | "false" });
  const [page, setPage] = useState(1);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [resetResult, setResetResult] = useState<string | null>(null);

  const { data: usersData, isLoading: isUsersLoading } = useQuery({
    queryKey: ["admin", "users", filters, page],
    queryFn: () =>
      listUsers({
        page: page - 1,
        size: 20,
        search: filters.search || undefined,
        role: (filters.role as Role) || undefined,
        isActive: filters.isActive === "" ? undefined : filters.isActive === "true",
      }),
  });

  const { data: companiesData } = useQuery({
    queryKey: ["admin", "companies", "all"],
    queryFn: () => listCompanies({ size: 1000 }),
  });

  const companiesById = useMemo(() => {
    const map = new Map<string, Company>();
    companiesData?.items.forEach((c) => map.set(c.id, c));
    return map;
  }, [companiesData]);

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      push("Пользователь создан", "success");
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
      setIsFormOpen(false);
    },
    onError: (err: { message?: string }) => push(err.message ?? "Не удалось создать пользователя", "error"),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Parameters<typeof updateUser>[1] }) => updateUser(id, body),
    onSuccess: () => {
      push("Пользователь обновлён", "success");
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
      setEditingUser(null);
    },
    onError: (err: { message?: string }) => push(err.message ?? "Не удалось обновить пользователя", "error"),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteUser,
    onSuccess: () => {
      push("Пользователь удалён", "success");
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
    onError: (err: { message?: string }) => push(err.message ?? "Не удалось удалить пользователя", "error"),
  });

  const resetMutation = useMutation({
    mutationFn: resetUserPassword,
    onSuccess: (data) => {
      setResetResult(data.temporaryPassword);
    },
    onError: (err: { message?: string }) => push(err.message ?? "Не удалось сбросить пароль", "error"),
  });

  const handleFormSubmit = (values: UserForm) => {
    const body = {
      ...values,
      phone: values.phone || null,
      companyId: values.companyId || null,
      password: values.password || undefined,
    };
    if (editingUser) {
      updateMutation.mutate({ id: editingUser.id, body });
    } else {
      createMutation.mutate(body);
    }
  };

  const handleEdit = (user: User) => {
    setEditingUser(user);
    setIsFormOpen(true);
  };

  const handleCloseForm = () => {
    setIsFormOpen(false);
    setEditingUser(null);
  };

  const handleDelete = (user: User) => {
    if (confirm(`Удалить пользователя ${user.fullName}?`)) {
      deleteMutation.mutate(user.id);
    }
  };

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Пользователи</h1>
        <Button onClick={() => setIsFormOpen(true)}>Добавить пользователя</Button>
      </header>

      <div className={styles.filters}>
        <Input
          placeholder="Поиск по ФИО, email, телефону"
          value={filters.search}
          onChange={(e) => setFilters((f) => ({ ...f, search: e.target.value }))}
        />
        <Select
          placeholder="Все роли"
          options={ROLES}
          value={filters.role}
          onChange={(e) => setFilters((f) => ({ ...f, role: e.target.value }))}
        />
        <Select
          placeholder="Все статусы"
          options={[
            { value: "true", label: "Активен" },
            { value: "false", label: "Неактивен" },
          ]}
          value={filters.isActive}
          onChange={(e) => setFilters((f) => ({ ...f, isActive: e.target.value as "" | "true" | "false" }))}
        />
      </div>

      <Table<User>
        columns={[
          { key: "fullName", title: "ФИО" },
          { key: "email", title: "Email" },
          { key: "phone", title: "Телефон" },
          { key: "role", title: "Роль", render: (row) => roleLabel(row.role) },
          {
            key: "company",
            title: "Компания",
            render: (row) => (row.companyId ? companiesById.get(row.companyId)?.name ?? "—" : "—"),
          },
          {
            key: "isActive",
            title: "Статус",
            render: (row) => (row.isActive ? "Активен" : "Неактивен"),
          },
          {
            key: "actions",
            title: "Действия",
            render: (row) => (
              <div className={styles.actions}>
                <Button size="sm" variant="secondary" onClick={() => handleEdit(row)}>
                  Изменить
                </Button>
                <Button size="sm" variant="secondary" onClick={() => resetMutation.mutate(row.id)}>
                  Сбросить пароль
                </Button>
                <Button size="sm" variant="secondary" onClick={() => handleDelete(row)}>
                  Удалить
                </Button>
              </div>
            ),
          },
        ]}
        data={usersData?.items ?? []}
        rowKey={(row) => row.id}
        loading={isUsersLoading}
      />

      {usersData && usersData.total > 0 && (
        <div className={styles.pagination}>
          <Pagination page={page} size={20} total={usersData.total} onChange={setPage} />
        </div>
      )}

      <UserFormModal
        open={isFormOpen}
        user={editingUser}
        companies={companiesData?.items ?? []}
        onClose={handleCloseForm}
        onSubmit={handleFormSubmit}
        isSubmitting={createMutation.isPending || updateMutation.isPending}
      />

      <Modal open={!!resetResult} title="Временный пароль" onClose={() => setResetResult(null)}>
        <p className={styles.hint}>Скопируйте пароль — он показывается один раз.</p>
        <Input readOnly value={resetResult ?? ""} />
      </Modal>
    </div>
  );
}
