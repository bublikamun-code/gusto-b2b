package by.gusto.file.service;

import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.file.dto.ProductImageResponse;
import by.gusto.file.entity.FileEntity;
import by.gusto.file.entity.ProductImage;
import by.gusto.file.repository.FileRepository;
import by.gusto.file.repository.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final FileRepository fileRepository;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public List<ProductImageResponse> getImages(UUID productId) {
        return productImageRepository.findByProductIdOrderBySortAsc(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductImageResponse attachImage(UUID productId, UUID fileId, Integer sort) {
        if (productImageRepository.existsByProductIdAndFileId(productId, fileId)) {
            throw new GustoException(ErrorCode.CONFLICT, "Это изображение уже прикреплено к товару");
        }

        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Файл не найден"));
        if (file.getVisibility() != FileEntity.Visibility.PUBLIC) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Изображение товара должно быть публичным");
        }

        ProductImage image = ProductImage.builder()
                .productId(productId)
                .fileId(fileId)
                .sort(sort != null ? sort : 0)
                .build();
        return toResponse(productImageRepository.save(image));
    }

    @Transactional
    public void detachImage(UUID productId, UUID imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Изображение не найдено"));
        if (!image.getProductId().equals(productId)) {
            throw new GustoException(ErrorCode.ACCESS_DENIED, "Изображение не принадлежит товару");
        }
        productImageRepository.delete(image);
    }

    private ProductImageResponse toResponse(ProductImage image) {
        return ProductImageResponse.builder()
                .id(image.getId())
                .fileId(image.getFileId())
                .url(fileService.buildPublicUrl(image.getFileId().toString()))
                .sort(image.getSort())
                .build();
    }
}
