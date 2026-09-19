package by.gusto.crm.repository;

import by.gusto.crm.entity.CrmTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CrmTaskRepository extends JpaRepository<CrmTaskEntity, UUID> {

    Page<CrmTaskEntity> findAllByAssigneeIdOrderByCreatedAtDesc(UUID assigneeId, Pageable pageable);

    Page<CrmTaskEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<CrmTaskEntity> findAllByAssigneeIdAndStatusOrderByDueDateAsc(UUID assigneeId,
            CrmTaskEntity.Status status, Pageable pageable);
}
