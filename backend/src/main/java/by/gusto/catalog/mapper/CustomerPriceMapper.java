package by.gusto.catalog.mapper;

import by.gusto.catalog.dto.CustomerPriceRequest;
import by.gusto.catalog.dto.CustomerPriceResponse;
import by.gusto.catalog.entity.CustomerPrice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CustomerPriceMapper {

    CustomerPriceResponse toResponse(CustomerPrice customerPrice);

    List<CustomerPriceResponse> toResponseList(List<CustomerPrice> customerPrices);

    @Mapping(target = "id", ignore = true)
    CustomerPrice toEntity(CustomerPriceRequest request);

    void updateEntity(@MappingTarget CustomerPrice customerPrice, CustomerPriceRequest request);
}
