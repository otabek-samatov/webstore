package cartservice.dto.kafka;

import cartservice.dto.CartItemDto;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;

@Data
@NoArgsConstructor
public class StockStatusKafka {

    private Collection<StockLevelDto> stockLevels = new ArrayList<>();
    private String actionType;

    public void addItem(CartItemDto cartItem) {

        StockLevelDto stockLevel = new StockLevelDto();
        stockLevel.setStockLevel(cartItem.getQuantity());
        stockLevel.setProductSKU(cartItem.getProductSKU());

        this.stockLevels.add(stockLevel);
    }

    public void addItems(Collection<CartItemDto> cartItems) {
        for (CartItemDto cartItemDto : cartItems) {
            addItem(cartItemDto);
        }

    }
}
