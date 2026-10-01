package by.gusto.order.repository;

import by.gusto.order.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    Page<OrderEntity> findAllByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId, Pageable pageable);

    Page<OrderEntity> findAllByCustomerCompanyIdOrderByCreatedAtDesc(UUID customerCompanyId, Pageable pageable);

    // «свои + пул» (2.7): заказы, взятые менеджером, и розничный пул «не назначено».
    // Пул — ТОЛЬКО заказы без компании. Раньше условие было `or o.managerId is null` без
    // проверки customer_company_id, поэтому менеджер видел в списке B2B-заказы чужих клиентов
    // (ФИО, телефон, адрес, суммы), хотя открыть их всё равно не мог — canAccessOrder скоупит
    // правильно (аудит 2026-09-30, P0-1).
    @Query("select o from OrderEntity o where "
            + "(o.customerUserId = :userId or o.managerId = :userId "
            + " or (o.managerId is null and o.customerCompanyId is null)) "
            + "order by o.createdAt desc")
    Page<OrderEntity> findAllVisibleTo(@Param("userId") UUID userId, Pageable pageable);

    // S22: менеджерский список — свои + пул «не назначено» (2.7), опциональный фильтр по статусу
    Page<OrderEntity> findAllByManagerIdOrderByCreatedAtDesc(UUID managerId, Pageable pageable);

    Page<OrderEntity> findAllByManagerIdAndStatusOrderByCreatedAtDesc(UUID managerId,
            OrderEntity.Status status, Pageable pageable);

    // Пул «не назначено» — ТОЛЬКО розница. Методы с CustomerCompanyIdIsNull добавлены
    // в аудите 2026-09-30 (P0-1): прежние findAllByManagerIdIsNull* отдавали менеджеру
    // в пул ещё и B2B-заказы компаний, закреплённых за другими менеджерами.
    Page<OrderEntity> findAllByManagerIdIsNullAndCustomerCompanyIdIsNullOrderByCreatedAtDesc(Pageable pageable);

    Page<OrderEntity> findAllByManagerIdIsNullAndCustomerCompanyIdIsNullAndStatusOrderByCreatedAtDesc(
            OrderEntity.Status status, Pageable pageable);

    Page<OrderEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<OrderEntity> findAllByStatusOrderByCreatedAtDesc(OrderEntity.Status status, Pageable pageable);
}
