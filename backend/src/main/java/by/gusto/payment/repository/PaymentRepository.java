package by.gusto.payment.repository;

import by.gusto.payment.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    List<PaymentEntity> findAllByInvoiceIdOrderByPaidAtAsc(UUID invoiceId);

    @Query("select coalesce(sum(p.amount), 0) from PaymentEntity p "
            + "where p.invoiceId = :invoiceId")
    BigDecimal sumByInvoiceId(@Param("invoiceId") UUID invoiceId);
}
