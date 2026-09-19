package by.gusto.request.repository;

import by.gusto.request.entity.SiteRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SiteRequestRepository extends JpaRepository<SiteRequestEntity, UUID> {

    Page<SiteRequestEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<SiteRequestEntity> findAllByStatusOrderByCreatedAtDesc(SiteRequestEntity.Status status, Pageable pageable);
}
