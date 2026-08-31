package by.gusto.catalog.mapper;

import by.gusto.catalog.dto.CategoryRequest;
import by.gusto.catalog.dto.CategoryResponse;
import by.gusto.catalog.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toResponseList(List<Category> categories);

    @Mapping(target = "id", ignore = true)
    Category toEntity(CategoryRequest request);

    void updateEntity(@MappingTarget Category category, CategoryRequest request);
}
