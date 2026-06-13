package inventoryservice.dto.kafka;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;

@Data
@NoArgsConstructor
public class StockStatusKafka {
    private Collection<StockLevelDto> stockLevels = new ArrayList<>();
    private String actionType;
    private String orderId;
}
