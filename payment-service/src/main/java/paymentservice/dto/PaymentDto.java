package paymentservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import paymentservice.entities.PaymentStatus;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO for {@link paymentservice.entities.Payment}
 */
@Data
public class PaymentDto implements Serializable {
    Long id;

    @NotNull(message = "Order ID should be specified")
    Long orderId;

    @NotNull(message = "User ID should be specified")
    Long userId;

    PaymentStatus paymentStatus;

    @NotNull
    @PositiveOrZero(message = "Payment Amount should be non negative")
    BigDecimal amount;
}