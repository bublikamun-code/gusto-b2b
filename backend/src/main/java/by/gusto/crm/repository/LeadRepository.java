package by.gusto.crm.repository;

import by.gusto.crm.entity.LeadEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeadRepository extends JpaRepository<LeadEntity, UUID> {

    Page<LeadEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<LeadEntity> findAllByStatusOrderByCreatedAtDesc(LeadEntity.Status status, Pageable pageable);

    Page<LeadEntity> findAllByAssignedManagerIdOrderByCreatedAtDesc(UUID managerId, Pageable pageable);

    Page<LeadEntity> findAllByAssignedManagerIdAndStatusOrderByCreatedAtDesc(UUID managerId,
            LeadEntity.Status status, Pageable pageable);

    Page<LeadEntity> findAllByAssignedManagerIdIsNullOrderByCreatedAtDesc(Pageable pageable);

    Page<LeadEntity> findAllByAssignedManagerIdIsNullAndStatusOrderByCreatedAtDesc(
            LeadEntity.Status status, Pageable pageable);

    /** Пул + свои: для менеджера без явного scope (2.7). */
    Page<LeadEntity> findAllByAssignedManagerIdOrAssignedManagerIdIsNullOrderByCreatedAtDesc(
            UUID managerId, Pageable pageable);

    Page<LeadEntity> findAllByAssignedManagerIdOrAssignedManagerIdIsNullAndStatusOrderByCreatedAtDesc(
            UUID managerId, LeadEntity.Status status, Pageable pageable);

    long countByStatus(LeadEntity.Status status);
}
