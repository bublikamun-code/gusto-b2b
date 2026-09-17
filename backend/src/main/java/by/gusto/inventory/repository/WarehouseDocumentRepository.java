package by.gusto.inventory.repository;

import by.gusto.inventory.entity.WarehouseDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseDocumentRepository extends JpaRepository<WarehouseDocument, UUID> {

    Optional<WarehouseDocument> findByIdAndStatus(UUID id, WarehouseDocument.Status status);

    @Query("select d from WarehouseDocument d where "
            + "(:type is null or d.type = :type) "
            + "and (:status is null or d.status = :status) "
            + "and (:locationId is null or d.locationFromId = :locationId or d.locationToId = :locationId) "
            + "order by d.createdAt desc")
    Page<WarehouseDocument> search(@Param("type") WarehouseDocument.Type type,
                                   @Param("status") WarehouseDocument.Status status,
                                   @Param("locationId") UUID locationId,
                                   Pageable pageable);
}
