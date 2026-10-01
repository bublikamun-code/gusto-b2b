package by.gusto.auth.repository;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    List<User> findAllByCompanyIdAndDeletedAtIsNull(UUID companyId);

    List<User> findAllByDeletedAtIsNull();

    /** Сколько активных неудалённых админов осталось в системе (защита от «последнего админа»). */
    long countByRoleAndActiveTrueAndDeletedAtIsNull(Role role);

    /**
     * Атомарно помечает TOTP-шаг использованным: сработает, только если счётчик
     * не ушёл вперёд. Так два параллельных входа с одним кодом не пройдут оба (S44).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.totpLastCounter = :counter "
            + "where u.id = :id and (u.totpLastCounter is null or u.totpLastCounter < :counter)")
    int consumeTotpCounter(@Param("id") UUID id, @Param("counter") long counter);
}
