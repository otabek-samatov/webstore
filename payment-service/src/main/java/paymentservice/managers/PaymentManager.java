package paymentservice.managers;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import paymentservice.dto.PaymentDto;
import paymentservice.dto.RefundDto;
import paymentservice.entities.Payment;
import paymentservice.entities.PaymentStatus;
import paymentservice.entities.Refund;
import paymentservice.entities.RefundStatus;
import paymentservice.mappers.PaymentMapper;
import paymentservice.mappers.RefundMapper;
import paymentservice.repositories.PaymentRepository;
import paymentservice.repositories.RefundRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class PaymentManager {

    private final PaymentProcess paymentProcess;

    private final PaymentMapper paymentMapper;

    private final RefundMapper refundMapper;

    private final PaymentRepository paymentRepository;

    private final RefundRepository refundRepository;

    @Transactional
    public Payment processPayment(PaymentDto paymentDto) {
        if (paymentDto == null) {
            throw new IllegalArgumentException("paymentDto is null");
        }

        if (paymentDto.getOrderId() == null) {
            throw new IllegalArgumentException("order ID is null");
        }

        if (paymentDto.getUserId() == null) {
            throw new IllegalArgumentException("user ID is null");
        }

        if (Optional.ofNullable(paymentDto.getAmount()).orElse(BigDecimal.ZERO).compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("payment amount should be non negative");
        }

        Integer count = paymentRepository.getCountByOrderAndStatus(paymentDto.getOrderId(), PaymentStatus.COMPLETED);
        if (count > 0) {
            throw new IllegalArgumentException("payment has already been correctly processed !");
        }

        Payment payment = paymentMapper.toEntity(paymentDto);
        boolean success = paymentProcess.processPayment(payment);
        if (success) {
            payment.setPaymentStatus(PaymentStatus.COMPLETED);
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
        }

        paymentRepository.save(payment);

        updateOrderStatus(payment);

        return payment;
    }

    public Payment getPaymentById(Long paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("payment ID is null");
        }

        return paymentRepository.findById(paymentId).orElseThrow(() ->
                new EntityNotFoundException("Payment id = " + paymentId + " not found")
        );
    }

    public Payment getPaymentByOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("order ID is null");
        }

        return paymentRepository.findPaymentByOrderId(orderId).orElseThrow(() ->
                new EntityNotFoundException("Payment with order id = " + orderId + " not found")
        );
    }

    @Transactional
    public Refund processRefund(RefundDto refundDto) {
        if (refundDto == null) {
            throw new IllegalArgumentException("refundDto is null");
        }

        if (refundDto.getPaymentId() == null) {
            throw new IllegalArgumentException("Payment ID is null");
        }

        if (Optional.ofNullable(refundDto.getRefundAmount()).orElse(BigDecimal.ZERO).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("refund amount should be positive");
        }

        Payment payment = getPaymentById(refundDto.getPaymentId());

        if (payment.getPaymentStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalArgumentException("Can only refund completed payments");
        }

        if (refundDto.getRefundAmount().compareTo(payment.getAmount()) != 0) {
            throw new IllegalArgumentException("Refund amount should be equal to payment amount");
        }

        Integer refundedPaymentsCount = refundRepository.getCountByOrderAndStatus(payment.getOrderId(), RefundStatus.COMPLETED);
        if (refundedPaymentsCount > 0) {
            throw new IllegalArgumentException("payment has already been refunded !");
        }

        Refund refund = refundMapper.toEntity(refundDto);

        boolean success = paymentProcess.processRefund(payment);
        if (success) {
            refund.setRefundStatus(RefundStatus.COMPLETED);
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            updateOrderStatus(payment);
        } else {
            refund.setRefundStatus(RefundStatus.FAILED);
        }

        payment.addRefund(refund);

        paymentRepository.save(payment);

        return refund;
    }

    public Refund getRefundById(Long refundId) {
        if (refundId == null) {
            throw new IllegalArgumentException("refund ID is null");
        }

        return refundRepository.findById(refundId).orElseThrow(() ->
                new EntityNotFoundException("Refund id = " + refundId + " not found")
        );
    }

    public List<Refund> getRefundByPaymentId(Long paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("payment ID is null");
        }

        return refundRepository.findRefundByPaymentId(paymentId);
    }


    private void updateOrderStatus(Payment p) {
        throw new UnsupportedOperationException("updateOrderStatus is not supported yet.");
    }

}
