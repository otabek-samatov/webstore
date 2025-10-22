package paymentservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import paymentservice.entities.Refund;
import paymentservice.entities.RefundStatus;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    @Query("select r from Refund r where r.payment.id = :paymentId")
    List<Refund> findRefundByPaymentId(Long paymentId);

    @Query("select count(0) from Refund r where r.payment.orderId = :orderId and r.refundStatus = :refundStatus")
    Integer getCountByOrderAndStatus(Long orderId, RefundStatus refundStatus);


}
