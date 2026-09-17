package by.gusto.inventory.repository;

import by.gusto.inventory.entity.StockLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLocationRepository extends JpaRepository<StockLocation, UUID> {

    List<StockLocation> findAllByActiveTrueOrderByNameAsc();

    Optional<StockLocation> findByNameIgnoreCase(String name);
}
