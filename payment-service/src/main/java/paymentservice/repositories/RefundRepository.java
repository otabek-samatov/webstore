package paymentservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import paymentservice.entities.Refund;

import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    @Query("select r from Refund r where r.payment.id = :paymentId")
    Optional<Refund> findRefundByPaymentId(Long paymentId);
}
