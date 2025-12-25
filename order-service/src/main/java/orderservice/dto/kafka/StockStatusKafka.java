package orderservice.dto.kafka;

import lombok.Data;
import lombok.NoArgsConstructor;
import orderservice.dto.OrderItemDto;

import java.util.ArrayList;
import java.util.Collection;

@Data
@NoArgsConstructor
public class StockStatusKafka {

    private Collection<OrderItemDto> orderItems;
    private String actionType;

    public void addOrderItem(OrderItemDto orderItem) {
        if (orderItems == null) {
            orderItems = new ArrayList<>();
        }

        this.orderItems.add(orderItem);
    }
}
