package by.gusto.catalog.mapper;

import by.gusto.catalog.dto.PriceListRequest;
import by.gusto.catalog.dto.PriceListResponse;
import by.gusto.catalog.entity.PriceList;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PriceListMapper {

    PriceListResponse toResponse(PriceList priceList);

    List<PriceListResponse> toResponseList(List<PriceList> priceLists);

    @Mapping(target = "id", ignore = true)
    PriceList toEntity(PriceListRequest request);

    void updateEntity(@MappingTarget PriceList priceList, PriceListRequest request);
}
