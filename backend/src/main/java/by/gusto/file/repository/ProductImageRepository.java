package by.gusto.file.repository;

import by.gusto.file.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortAsc(UUID productId);

    List<ProductImage> findByProductIdInOrderBySortAsc(List<UUID> productIds);

    boolean existsByFileId(UUID fileId);

    boolean existsByProductIdAndFileId(UUID productId, UUID fileId);

    void deleteByProductIdAndFileId(UUID productId, UUID fileId);
}
