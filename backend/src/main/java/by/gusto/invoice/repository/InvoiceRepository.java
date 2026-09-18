package by.gusto.invoice.repository;

import by.gusto.invoice.entity.InvoiceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {

    Optional<InvoiceEntity> findByOrderId(UUID orderId);

    Page<InvoiceEntity> findAllByCustomerCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    Page<InvoiceEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Свои счета менеджера: заказы, взятые им в работу, и компании за ним (2.1). */
    @Query("select i from InvoiceEntity i where "
            + "i.orderId in (select o.id from by.gusto.order.entity.OrderEntity o where o.managerId = :managerId) "
            + "or i.customerCompanyId in (select c.id from by.gusto.company.entity.Company c where c.managerId = :managerId) "
            + "order by i.createdAt desc")
    Page<InvoiceEntity> findAllVisibleTo(@Param("managerId") UUID managerId, Pageable pageable);
}
