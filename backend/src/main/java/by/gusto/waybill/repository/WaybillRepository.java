package by.gusto.waybill.repository;

import by.gusto.waybill.entity.WaybillEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface WaybillRepository extends JpaRepository<WaybillEntity, UUID> {

    Page<WaybillEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<WaybillEntity> findAllByOrderId(UUID orderId, Pageable pageable);

    /** Свои накладные менеджера: заказы в работе + закреплённые компании (2.1). */
    @Query("select w from WaybillEntity w where "
            + "w.orderId in (select o.id from by.gusto.order.entity.OrderEntity o where o.managerId = :managerId) "
            + "or w.orderId in (select o2.id from by.gusto.order.entity.OrderEntity o2 "
            + "   where o2.customerCompanyId in "
            + "       (select c.id from by.gusto.company.entity.Company c where c.managerId = :managerId)) "
            + "order by w.createdAt desc")
    Page<WaybillEntity> findAllVisibleTo(@Param("managerId") UUID managerId, Pageable pageable);

    /** Накладные заказов компании клиента (кабинет юрлица). */
    @Query("select w from WaybillEntity w where "
            + "w.orderId in (select o.id from by.gusto.order.entity.OrderEntity o "
            + "   where o.customerCompanyId = :companyId) "
            + "order by w.createdAt desc")
    Page<WaybillEntity> findAllByCompanyOrderByCreatedAtDesc(@Param("companyId") UUID companyId, Pageable pageable);
}
