package by.gusto.crm.repository;

import by.gusto.crm.entity.CrmNoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CrmNoteRepository extends JpaRepository<CrmNoteEntity, UUID> {

    List<CrmNoteEntity> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
