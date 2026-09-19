package by.gusto.waybill.repository;

import by.gusto.waybill.entity.WaybillItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WaybillItemRepository extends JpaRepository<WaybillItem, UUID> {

    List<WaybillItem> findAllByWaybillId(UUID waybillId);
}
