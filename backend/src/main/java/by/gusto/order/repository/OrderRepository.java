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

    @Query("select o from OrderEntity o where "
            + "(o.customerUserId = :userId or o.managerId = :userId) "
            + "order by o.createdAt desc")
    Page<OrderEntity> findAllVisibleTo(@Param("userId") UUID userId, Pageable pageable);

    // S22: менеджерский список — свои + пул «не назначено» (2.7), опциональный фильтр по статусу
    Page<OrderEntity> findAllByManagerIdOrderByCreatedAtDesc(UUID managerId, Pageable pageable);

    Page<OrderEntity> findAllByManagerIdAndStatusOrderByCreatedAtDesc(UUID managerId,
            OrderEntity.Status status, Pageable pageable);

    Page<OrderEntity> findAllByManagerIdIsNullOrderByCreatedAtDesc(Pageable pageable);

    Page<OrderEntity> findAllByManagerIdIsNullAndStatusOrderByCreatedAtDesc(
            OrderEntity.Status status, Pageable pageable);

    Page<OrderEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<OrderEntity> findAllByStatusOrderByCreatedAtDesc(OrderEntity.Status status, Pageable pageable);
}
