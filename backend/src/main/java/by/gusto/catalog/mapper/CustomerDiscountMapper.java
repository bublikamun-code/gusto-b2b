package by.gusto.catalog.mapper;

import by.gusto.catalog.dto.CustomerDiscountRequest;
import by.gusto.catalog.dto.CustomerDiscountResponse;
import by.gusto.catalog.entity.CustomerDiscount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CustomerDiscountMapper {

    CustomerDiscountResponse toResponse(CustomerDiscount customerDiscount);

    List<CustomerDiscountResponse> toResponseList(List<CustomerDiscount> customerDiscounts);

    @Mapping(target = "id", ignore = true)
    CustomerDiscount toEntity(CustomerDiscountRequest request);

    void updateEntity(@MappingTarget CustomerDiscount customerDiscount, CustomerDiscountRequest request);
}
