package by.gusto.catalog.mapper;

import by.gusto.catalog.dto.ProductPriceRequest;
import by.gusto.catalog.dto.ProductPriceResponse;
import by.gusto.catalog.entity.ProductPrice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductPriceMapper {

    ProductPriceResponse toResponse(ProductPrice productPrice);

    List<ProductPriceResponse> toResponseList(List<ProductPrice> productPrices);

    @Mapping(target = "id", ignore = true)
    ProductPrice toEntity(ProductPriceRequest request);

    void updateEntity(@MappingTarget ProductPrice productPrice, ProductPriceRequest request);
}
