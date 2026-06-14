package paymentservice.dto.kafka;

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class PaymentStatusMessage {

    private long orderId;
    private String status;

}
