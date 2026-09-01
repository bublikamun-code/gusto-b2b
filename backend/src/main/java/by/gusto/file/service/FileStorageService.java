package by.gusto.file.service;

import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageProperties properties;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(resolveRoot());
        } catch (IOException e) {
            throw new GustoException(ErrorCode.INTERNAL, "Не удалось инициализировать хранилище файлов");
        }
    }

    public void store(InputStream inputStream, String storageKey) {
        Path target = resolvePath(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new GustoException(ErrorCode.INTERNAL, "Ошибка сохранения файла");
        }
    }

    public InputStream load(String storageKey) {
        Path source = resolvePath(storageKey);
        try {
            return Files.newInputStream(source);
        } catch (IOException e) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Файл не найден в хранилище");
        }
    }

    public void delete(String storageKey) {
        Path source = resolvePath(storageKey);
        try {
            Files.deleteIfExists(source);
        } catch (IOException e) {
            throw new GustoException(ErrorCode.INTERNAL, "Ошибка удаления файла");
        }
    }

    public boolean exists(String storageKey) {
        return Files.exists(resolvePath(storageKey));
    }

    public long size(String storageKey) {
        try {
            return Files.size(resolvePath(storageKey));
        } catch (IOException e) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Файл не найден в хранилище");
        }
    }

    private Path resolvePath(String storageKey) {
        String prefix = storageKey.substring(0, 2).toLowerCase();
        return resolveRoot().resolve(prefix).resolve(storageKey);
    }

    private Path resolveRoot() {
        return Paths.get(properties.getPath()).toAbsolutePath().normalize();
    }
}
