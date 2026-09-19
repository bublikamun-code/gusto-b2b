package by.gusto.waybill.controller;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.file.entity.FileEntity;
import by.gusto.waybill.dto.WaybillDtos.CreateRequest;
import by.gusto.waybill.dto.WaybillDtos.WaybillResponse;
import by.gusto.waybill.service.WaybillService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * ТН/ТТН в бэк-офисе (S26). Оформление — ADMIN/ACCOUNTANT/MANAGER (матрица 2.1);
 * менеджер видит накладные своих клиентов; PDF — PRIVATE со стримингом (1.6).
 */
@RestController
@RequestMapping("/api/v1/waybills")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
public class WaybillController {

    private final WaybillService waybillService;
    private final AuthContext authContext;

    @PostMapping
    public ResponseEntity<ApiResponse<WaybillResponse>> create(@Valid @RequestBody CreateRequest request) {
        User actor = authContext.getCurrentUser();
        WaybillResponse response = waybillService.create(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WaybillResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<WaybillResponse> result = waybillService.list(authContext.getCurrentUser(), page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WaybillResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                waybillService.getById(id, authContext.getCurrentUser())));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<InputStreamResource> pdf(@PathVariable UUID id) {
        User actor = authContext.getCurrentUser();
        waybillService.getById(id, actor); // права
        FileEntity file = waybillService.ensurePdf(id, actor.getId());
        InputStream stream = waybillService.load(file);
        String filename = URLDecoder.decode(file.getOriginalName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(stream));
    }
}
