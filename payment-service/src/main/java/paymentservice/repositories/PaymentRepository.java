package paymentservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import paymentservice.entities.Payment;
import paymentservice.entities.PaymentStatus;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findPaymentByOrderId(Long orderId);


    @Query("select count(0) from Payment p where p.orderId = :orderId and p.paymentStatus = :paymentStatus")
    Integer getCountByOrderAndStatus(Long orderId, PaymentStatus paymentStatus);



}
