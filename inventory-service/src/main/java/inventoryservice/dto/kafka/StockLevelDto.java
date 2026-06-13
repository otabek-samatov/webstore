package inventoryservice.dto.kafka;

import inventoryservice.dto.InventoryDto;
import lombok.Data;

@Data
public class StockLevelDto {

    private Long stockLevel = 0L;

    private Long reservedStock = 0L;

    private String productSKU = "";

    public InventoryDto toInventoryDto() {
        InventoryDto inventoryDto = new InventoryDto();
        inventoryDto.setStockLevel(stockLevel);
        inventoryDto.setReservedStock(reservedStock);
        inventoryDto.setProductSKU(productSKU);
        return inventoryDto;
    }
}
