package orderservice.dto.kafka;

import lombok.Data;
import lombok.NoArgsConstructor;
import orderservice.dto.OrderItemDto;

import java.util.ArrayList;
import java.util.Collection;

@Data
@NoArgsConstructor
public class StockStatusKafka {

    private Collection<StockLevelDto> stockLevels = new ArrayList<>();
    private String actionType;

    public void addItem(OrderItemDto orderItemDto) {

        StockLevelDto stockLevel = new StockLevelDto();
        stockLevel.setStockLevel(orderItemDto.getQuantity());
        stockLevel.setProductSKU(orderItemDto.getProductSKU());

        this.stockLevels.add(stockLevel);
    }

    public void addItems(Collection<OrderItemDto> orderItems) {
        for (OrderItemDto orderItemDto : orderItems) {
            addItem(orderItemDto);
        }

    }
}
