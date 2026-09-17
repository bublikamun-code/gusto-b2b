package by.gusto.inventory.repository;

import by.gusto.inventory.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByIdAndStatusIn(UUID id, java.util.Collection<PurchaseOrder.Status> statuses);

    @Query("select p from PurchaseOrder p where "
            + "(:status is null or p.status = :status) "
            + "and (:supplierId is null or p.supplierId = :supplierId) "
            + "order by p.createdAt desc")
    Page<PurchaseOrder> search(@Param("status") PurchaseOrder.Status status,
                               @Param("supplierId") UUID supplierId,
                               Pageable pageable);
}
