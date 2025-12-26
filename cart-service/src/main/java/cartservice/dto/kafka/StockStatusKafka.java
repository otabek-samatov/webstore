package cartservice.dto.kafka;

import cartservice.dto.CartItemDto;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;

@Data
@NoArgsConstructor
public class StockStatusKafka {

    private Collection<CartItemDto> cartItems;
    private String actionType;

    public void addCartItem(CartItemDto cartItem) {
        if (cartItems == null) {
            cartItems = new ArrayList<>();
        }

        this.cartItems.add(cartItem);
    }
}
