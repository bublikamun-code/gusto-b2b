package by.gusto.catalog.service;

import by.gusto.catalog.dto.BrandRequest;
import by.gusto.catalog.dto.BrandResponse;
import by.gusto.catalog.entity.Brand;
import by.gusto.catalog.mapper.BrandMapper;
import by.gusto.catalog.repository.BrandRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final BrandMapper brandMapper;

    @Transactional(readOnly = true)
    public List<BrandResponse> getAll() {
        return brandMapper.toResponseList(brandRepository.findAllByOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    public BrandResponse getById(UUID id) {
        Brand brand = findBrand(id);
        return brandMapper.toResponse(brand);
    }

    @Transactional
    public BrandResponse create(BrandRequest request) {
        validateUnique(request, null);
        Brand brand = brandMapper.toEntity(request);
        return brandMapper.toResponse(brandRepository.save(brand));
    }

    @Transactional
    public BrandResponse update(UUID id, BrandRequest request) {
        Brand brand = findBrand(id);
        validateUnique(request, id);
        brandMapper.updateEntity(brand, request);
        return brandMapper.toResponse(brandRepository.save(brand));
    }

    @Transactional
    public void delete(UUID id) {
        Brand brand = findBrand(id);
        brandRepository.delete(brand);
    }

    private Brand findBrand(UUID id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Бренд не найден"));
    }

    private void validateUnique(BrandRequest request, UUID excludeId) {
        boolean slugExists = excludeId == null
                ? brandRepository.existsBySlug(request.getSlug())
                : brandRepository.existsBySlugAndIdNot(request.getSlug(), excludeId);
        if (slugExists) {
            throw new GustoException(ErrorCode.CONFLICT, "Бренд с таким slug уже существует");
        }
        boolean nameExists = excludeId == null
                ? brandRepository.existsByName(request.getName())
                : brandRepository.existsByNameAndIdNot(request.getName(), excludeId);
        if (nameExists) {
            throw new GustoException(ErrorCode.CONFLICT, "Бренд с таким названием уже существует");
        }
    }
}
