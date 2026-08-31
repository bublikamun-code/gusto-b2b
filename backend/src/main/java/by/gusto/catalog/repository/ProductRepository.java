package by.gusto.catalog.repository;

import by.gusto.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySkuAndDeletedAtIsNull(String sku);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNotAndDeletedAtIsNull(String sku, UUID id);

    @EntityGraph(attributePaths = {})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);
}
