package by.gusto.file.repository;

import by.gusto.file.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, UUID> {

    Optional<FileEntity> findByStorageKey(String storageKey);

    boolean existsByStorageKey(String storageKey);
}
