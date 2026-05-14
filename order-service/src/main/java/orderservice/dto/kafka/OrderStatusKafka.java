package orderservice.dto.kafka;

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class OrderStatusKafka {

    private Long orderId;
    private String actionType;

}
