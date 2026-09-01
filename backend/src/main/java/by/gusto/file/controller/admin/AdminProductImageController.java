package by.gusto.file.controller.admin;

import by.gusto.common.api.ApiResponse;
import by.gusto.file.dto.FileResponse;
import by.gusto.file.dto.ProductImageResponse;
import by.gusto.file.entity.FileEntity;
import by.gusto.file.service.FileService;
import by.gusto.file.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/catalog/products/{productId}/images")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductImageController {

    private final ProductImageService productImageService;
    private final FileService fileService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> getImages(@PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.success(productImageService.getImages(productId)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductImageResponse>> uploadImage(
            @PathVariable UUID productId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sort", defaultValue = "0") Integer sort) {
        FileResponse uploaded = fileService.upload(file, FileEntity.Visibility.PUBLIC);
        return ResponseEntity.status(201).body(ApiResponse.success(
                productImageService.attachImage(productId, uploaded.getId(), sort)));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<ApiResponse<Void>> detachImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        productImageService.detachImage(productId, imageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
