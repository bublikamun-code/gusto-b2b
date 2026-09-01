package by.gusto.file.controller;

import by.gusto.common.api.ApiResponse;
import by.gusto.file.dto.FileResponse;
import by.gusto.file.entity.FileEntity;
import by.gusto.file.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
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

import java.io.InputStream;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FileResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") FileEntity.Visibility visibility) {
        return ResponseEntity.status(201).body(ApiResponse.success(fileService.upload(file, visibility)));
    }

    @GetMapping("/{storageKey}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String storageKey) {
        InputStream inputStream = fileService.download(storageKey);
        FileEntity file = fileService.getFile(storageKey);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + file.getOriginalName() + "\"")
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .body(new InputStreamResource(inputStream));
    }

    @DeleteMapping("/{storageKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String storageKey) {
        fileService.delete(storageKey);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
