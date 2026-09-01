package by.gusto.auth.repository;

import by.gusto.auth.entity.RecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    List<RecoveryCode> findAllByUserIdAndUsedFalse(UUID userId);

    Optional<RecoveryCode> findByUserIdAndCodeHash(UUID userId, String codeHash);
}
