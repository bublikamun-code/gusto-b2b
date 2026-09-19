package by.gusto.notification.repository;

import by.gusto.notification.entity.NotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, UUID> {

    List<NotificationSubscription> findAllByUserIdAndChannelAndActiveTrue(UUID userId,
            NotificationSubscription.Channel channel);

    List<NotificationSubscription> findAllByUserId(UUID userId);

    /** Активные Telegram-подписки пользователей с ролью (менеджеры — пул событий). */
    @Query("select s from NotificationSubscription s join by.gusto.auth.entity.User u "
            + "on s.userId = u.id where s.channel = 'TELEGRAM' and s.active = true and u.role = :role "
            + "and u.deletedAt is null")
    List<NotificationSubscription> findActiveByRole(@Param("role") by.gusto.auth.entity.Role role);

    /** Активные Telegram-подписки сотрудников компании клиента. */
    @Query("select s from NotificationSubscription s join by.gusto.auth.entity.User u "
            + "on s.userId = u.id where s.channel = 'TELEGRAM' and s.active = true "
            + "and u.companyId = :companyId and u.deletedAt is null")
    List<NotificationSubscription> findActiveByCompanyId(@Param("companyId") UUID companyId);
}
