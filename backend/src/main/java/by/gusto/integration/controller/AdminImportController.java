package by.gusto.integration.controller;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.integration.dto.ImportPreview;
import by.gusto.integration.dto.ImportReport;
import by.gusto.integration.service.XlsxImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Импорт из 1С (S35): загрузка .xlsx с прайсами и остатками.
 * Запуск — только ADMIN/ACCOUNTANT (матрица 2.1); UI-мастер — S38.
 */
@RestController
@RequestMapping("/api/v1/admin/import")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class AdminImportController {

    private final XlsxImportService importService;
    private final AuthContext authContext;

    @PostMapping("/prices")
    public ResponseEntity<ApiResponse<ImportReport>> prices(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean archiveMissing) {
        User actor = authContext.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(
                importService.importPrices(file, archiveMissing, actor)));
    }

    @PostMapping("/stock")
    public ResponseEntity<ApiResponse<ImportReport>> stock(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                importService.importStock(file, authContext.getCurrentUser())));
    }

    /**
     * Предпросмотр (S38): мастер разбирает файл и показывает строки/ошибки
     * до применения. Только чтение — integration_files не создаётся.
     */
    @PostMapping("/{type}/preview")
    public ResponseEntity<ApiResponse<ImportPreview>> preview(
            @PathVariable String type,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(importService.preview(file, type)));
    }
}
