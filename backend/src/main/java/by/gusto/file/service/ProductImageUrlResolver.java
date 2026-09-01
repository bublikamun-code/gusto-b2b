package by.gusto.file.service;

import by.gusto.file.entity.FileEntity;
import by.gusto.file.entity.ProductImage;
import by.gusto.file.repository.FileRepository;
import by.gusto.file.repository.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductImageUrlResolver {

    private final ProductImageRepository productImageRepository;
    private final FileRepository fileRepository;

    @Transactional(readOnly = true)
    public Map<UUID, List<String>> resolveUrls(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }

        List<ProductImage> images = productImageRepository.findByProductIdInOrderBySortAsc(productIds);

        Map<UUID, String> fileKeys = fileRepository.findAllById(
                        images.stream().map(ProductImage::getFileId).distinct().toList())
                .stream()
                .filter(f -> f.getVisibility() == FileEntity.Visibility.PUBLIC)
                .collect(Collectors.toMap(FileEntity::getId, FileEntity::getStorageKey));

        return images.stream()
                .collect(Collectors.groupingBy(
                        ProductImage::getProductId,
                        Collectors.mapping(
                                pi -> "/api/v1/files/" + fileKeys.getOrDefault(pi.getFileId(), ""),
                                Collectors.toList())))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().filter(s -> !s.endsWith("/")).toList()));
    }

    @Transactional(readOnly = true)
    public List<String> resolveUrls(UUID productId) {
        return resolveUrls(List.of(productId)).getOrDefault(productId, Collections.emptyList());
    }
}
