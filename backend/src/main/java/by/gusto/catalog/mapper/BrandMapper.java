package by.gusto.catalog.mapper;

import by.gusto.catalog.dto.BrandRequest;
import by.gusto.catalog.dto.BrandResponse;
import by.gusto.catalog.entity.Brand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BrandMapper {

    BrandResponse toResponse(Brand brand);

    List<BrandResponse> toResponseList(List<Brand> brands);

    @Mapping(target = "id", ignore = true)
    Brand toEntity(BrandRequest request);

    void updateEntity(@MappingTarget Brand brand, BrandRequest request);
}
