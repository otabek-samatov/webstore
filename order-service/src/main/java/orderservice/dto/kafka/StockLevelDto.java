package orderservice.dto.kafka;

import lombok.Data;

@Data
public class StockLevelDto {

    private Long stockLevel = 0L;

    private Long reservedStock = 0L;

    private String productSKU = "";
}
