package by.gusto.catalog.service;

import by.gusto.catalog.dto.CategoryRequest;
import by.gusto.catalog.dto.CategoryResponse;
import by.gusto.catalog.entity.Category;
import by.gusto.catalog.mapper.CategoryMapper;
import by.gusto.catalog.repository.CategoryRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveTree() {
        List<Category> all = categoryRepository.findAllByActiveTrueOrderBySortAsc();
        return buildTree(all);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryMapper.toResponseList(categoryRepository.findAll());
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(UUID id) {
        Category category = findCategory(id);
        return categoryMapper.toResponse(category);
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        validateSlug(request.getSlug(), null);
        validateParent(request.getParentId());
        Category category = categoryMapper.toEntity(request);
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = findCategory(id);
        validateSlug(request.getSlug(), id);
        validateParent(request.getParentId(), id);
        categoryMapper.updateEntity(category, request);
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        Category category = findCategory(id);
        List<Category> children = categoryRepository.findAllByParentIdAndActiveTrueOrderBySortAsc(id);
        if (!children.isEmpty()) {
            throw new GustoException(ErrorCode.CONFLICT, "Нельзя удалить категорию с дочерними элементами");
        }
        categoryRepository.delete(category);
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Категория не найдена"));
    }

    private void validateSlug(String slug, UUID excludeId) {
        boolean exists = excludeId == null
                ? categoryRepository.existsBySlug(slug)
                : categoryRepository.existsBySlugAndIdNot(slug, excludeId);
        if (exists) {
            throw new GustoException(ErrorCode.CONFLICT, "Категория с таким slug уже существует");
        }
    }

    private void validateParent(UUID parentId) {
        validateParent(parentId, null);
    }

    private void validateParent(UUID parentId, UUID excludeId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(excludeId)) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Категория не может быть родителем самой себя");
        }
        if (!categoryRepository.existsById(parentId)) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Родительская категория не найдена");
        }
    }

    private List<CategoryResponse> buildTree(List<Category> categories) {
        Map<UUID, CategoryResponse> map = categories.stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toMap(CategoryResponse::getId, r -> r));
        List<CategoryResponse> roots = new ArrayList<>();
        for (CategoryResponse response : map.values()) {
            if (response.getParentId() == null) {
                roots.add(response);
            } else {
                CategoryResponse parent = map.get(response.getParentId());
                if (parent != null) {
                    parent.getChildren().add(response);
                }
            }
        }
        return roots;
    }
}
