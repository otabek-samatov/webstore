package orderservice.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Client-side view of payment-service's payment payload, used by
 * {@code ProcessPaymentStep} to call {@code POST /v1/payments}.
 * <p>
 * {@code paymentStatus} is modelled as a plain String (not the payment-service
 * enum, which lives in another module) — payment-service sets it on the
 * response to {@code COMPLETED} / {@code FAILED}.
 */
@Data
public class PaymentDto implements Serializable {

    private Long id;
    private Long orderId;
    private Long userId;
    private String paymentStatus;
    private BigDecimal amount;
}
