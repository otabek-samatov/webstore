package paymentservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import paymentservice.entities.RefundStatus;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO for {@link paymentservice.entities.Refund}
 */
@Data
public class RefundDto implements Serializable {

    Long id;

    @NotNull(message = "Payment ID should be specified")
    Long paymentId;

    @NotNull
    @Positive(message = "Refund Amount should be greater than zero")
    BigDecimal refundAmount;

    RefundStatus refundStatus;
}