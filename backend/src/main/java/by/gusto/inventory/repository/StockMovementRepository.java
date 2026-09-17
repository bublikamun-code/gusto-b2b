package by.gusto.inventory.repository;

import by.gusto.inventory.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findAllByProductIdAndLocationIdOrderByCreatedAtDesc(UUID productId, UUID locationId);
}
