package by.gusto.cabinet.controller;

import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.file.entity.FileEntity;
import by.gusto.waybill.dto.WaybillDtos.WaybillResponse;
import by.gusto.waybill.service.WaybillService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Кабинет юрлица: накладные заказов своей компании (S26; матрица 2.1).
 */
@RestController
@RequestMapping("/api/v1/cabinet/waybills")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER_LEGAL','ADMIN')")
public class CabinetWaybillController {

    private final WaybillService waybillService;
    private final AuthContext authContext;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WaybillResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var user = authContext.getCurrentUser();
        if (user.getCompanyId() == null) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "К пользователю не привязана компания");
        }
        Page<WaybillResponse> result = waybillService.listForCompany(user.getCompanyId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent()));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<InputStreamResource> pdf(@PathVariable UUID id) {
        var user = authContext.getCurrentUser();
        waybillService.getById(id, user); // проверка принадлежности компании
        FileEntity file = waybillService.ensurePdf(id, user.getId());
        InputStream stream = waybillService.load(file);
        String filename = URLDecoder.decode(file.getOriginalName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(stream));
    }
}
