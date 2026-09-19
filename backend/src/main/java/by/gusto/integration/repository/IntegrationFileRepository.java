package by.gusto.integration.repository;

import by.gusto.integration.entity.IntegrationFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IntegrationFileRepository extends JpaRepository<IntegrationFileEntity, UUID> {
}
