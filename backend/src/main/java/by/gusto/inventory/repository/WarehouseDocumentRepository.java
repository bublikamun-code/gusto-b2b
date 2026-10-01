package by.gusto.inventory.repository;

import by.gusto.inventory.entity.WarehouseDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseDocumentRepository extends JpaRepository<WarehouseDocument, UUID> {

    Optional<WarehouseDocument> findByIdAndStatus(UUID id, WarehouseDocument.Status status);

    /**
     * Чтение с пессимистической блокировкой строки — для подтверждения документа.
     * Без неё два параллельных confirm читали статус DRAFT до записи кем-либо и оба
     * применяли движения, списывая приход/списание дважды (аудит 2026-09-30, P0-3).
     * Второй поток теперь ждёт коммит первого и видит уже CONFIRMED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from WarehouseDocument d where d.id = :id")
    Optional<WarehouseDocument> findByIdForUpdate(@Param("id") UUID id);

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
