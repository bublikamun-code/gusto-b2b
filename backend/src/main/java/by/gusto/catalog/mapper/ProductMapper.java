package by.gusto.catalog.mapper;

import by.gusto.catalog.dto.ProductRequest;
import by.gusto.catalog.dto.ProductResponse;
import by.gusto.catalog.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductResponse toResponse(Product product);

    List<ProductResponse> toResponseList(List<Product> products);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "hit", ignore = true)
    @Mapping(target = "newProduct", ignore = true)
    Product toEntity(ProductRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "hit", ignore = true)
    @Mapping(target = "newProduct", ignore = true)
    void updateEntity(@MappingTarget Product product, ProductRequest request);

    /** Точечное переключение витринных флагов (S19.1), без затирания остальных полей. */
    default void applyShowcaseFlags(Product product, boolean hit, boolean newProduct) {
        product.setHit(hit);
        product.setNewProduct(newProduct);
    }
}
