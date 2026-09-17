package by.gusto.inventory.repository;

import by.gusto.inventory.entity.WarehouseDocumentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WarehouseDocumentItemRepository extends JpaRepository<WarehouseDocumentItem, UUID> {

    List<WarehouseDocumentItem> findAllByDocumentId(UUID documentId);

    void deleteAllByDocumentId(UUID documentId);
}
