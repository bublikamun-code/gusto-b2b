package by.gusto.file.service;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.file.dto.FileResponse;
import by.gusto.file.entity.FileEntity;
import by.gusto.file.repository.FileRepository;
import by.gusto.file.repository.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private static final List<String> IMAGE_MIME_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final FileStorageProperties properties;
    private final FileStorageService fileStorageService;
    private final FileRepository fileRepository;
    private final ProductImageRepository productImageRepository;
    private final AuthContext authContext;

    @Transactional
    public FileResponse upload(MultipartFile file, FileEntity.Visibility visibility) {
        validate(file);

        String storageKey = UUID.randomUUID().toString();
        String checksum = checksum(file);
        String detectedMimeType = detectMimeType(file);

        try (InputStream is = file.getInputStream()) {
            fileStorageService.store(is, storageKey);
        } catch (IOException e) {
            throw new GustoException(ErrorCode.INTERNAL, "Ошибка чтения загружаемого файла");
        }

        FileEntity entity = FileEntity.builder()
                .storageKey(storageKey)
                .originalName(file.getOriginalFilename())
                .mimeType(detectedMimeType)
                .sizeBytes(file.getSize())
                .checksum(checksum)
                .ownerId(currentUserId())
                .visibility(visibility)
                .build();

        return toResponse(fileRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public InputStream download(String storageKey) {
        FileEntity file = fileRepository.findByStorageKey(storageKey)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Файл не найден"));

        if (file.getVisibility() == FileEntity.Visibility.PRIVATE) {
            UUID currentUserId = currentUserId();
            if (currentUserId == null || !currentUserId.equals(file.getOwnerId()) && !isAdmin()) {
                throw new GustoException(ErrorCode.ACCESS_DENIED, "Доступ к файлу запрещён");
            }
        }

        return fileStorageService.load(storageKey);
    }

    @Transactional(readOnly = true)
    public FileEntity getFile(String storageKey) {
        return fileRepository.findByStorageKey(storageKey)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Файл не найден"));
    }

    @Transactional
    public void delete(String storageKey) {
        FileEntity file = fileRepository.findByStorageKey(storageKey)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Файл не найден"));

        UUID currentUserId = currentUserId();
        if (currentUserId == null || !currentUserId.equals(file.getOwnerId()) && !isAdmin()) {
            throw new GustoException(ErrorCode.ACCESS_DENIED, "Удаление файла запрещено");
        }

        if (productImageRepository.existsByFileId(file.getId())) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Файл используется как фото товара");
        }

        fileStorageService.delete(storageKey);
        fileRepository.delete(file);
    }

    public String buildPublicUrl(String storageKey) {
        return "/api/v1/files/" + storageKey;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Файл не передан или пуст");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "Размер файла превышает " + properties.getMaxFileSize() + " байт");
        }
        String mimeType = detectMimeType(file);
        if (!properties.getAllowedMimeTypes().contains(mimeType)) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Недопустимый тип файла: " + mimeType);
        }
    }

    private String detectMimeType(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = is.readNBytes(12);
            if (header.length < 2) {
                return file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            }
            if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
                return "image/jpeg";
            }
            if (startsWith(header, 0x89, 0x50, 0x4E, 0x47)) {
                return "image/png";
            }
            if (header.length >= 12 && startsWith(header, 0x52, 0x49, 0x46, 0x46)
                    && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
                return "image/webp";
            }
            return "application/octet-stream";
        } catch (IOException e) {
            throw new GustoException(ErrorCode.INTERNAL, "Ошибка чтения загружаемого файла");
        }
    }

    private boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private String checksum(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new GustoException(ErrorCode.INTERNAL, "Ошибка вычисления контрольной суммы");
        }
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        try {
            User currentUser = authContext.getCurrentUser();
            return currentUser != null ? currentUser.getId() : null;
        } catch (GustoException e) {
            return null;
        }
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    public FileResponse toResponse(FileEntity entity) {
        return FileResponse.builder()
                .id(entity.getId())
                .storageKey(entity.getStorageKey())
                .originalName(entity.getOriginalName())
                .mimeType(entity.getMimeType())
                .sizeBytes(entity.getSizeBytes())
                .visibility(entity.getVisibility())
                .url(buildPublicUrl(entity.getStorageKey()))
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
