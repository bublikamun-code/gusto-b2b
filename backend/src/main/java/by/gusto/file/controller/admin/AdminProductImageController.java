package by.gusto.file.controller.admin;

import by.gusto.common.api.ApiResponse;
import by.gusto.file.dto.ProductImageResponse;
import by.gusto.file.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/catalog/products/{productId}/images")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductImageController {

    private final ProductImageService productImageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> getImages(@PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.success(productImageService.getImages(productId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductImageResponse>> attachImage(
            @PathVariable UUID productId,
            @RequestParam("fileId") UUID fileId,
            @RequestParam(value = "sort", defaultValue = "0") Integer sort) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                productImageService.attachImage(productId, fileId, sort)));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<ApiResponse<Void>> detachImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        productImageService.detachImage(productId, imageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
