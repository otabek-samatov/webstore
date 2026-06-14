package paymentservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * DTO for {@link paymentservice.entities.Refund}
 */
@Data
public class RefundDto implements Serializable {

    Long id;

    @NotNull(message = "Payment ID should be specified")
    Long paymentId;

}