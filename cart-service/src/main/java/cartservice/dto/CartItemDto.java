package cartservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Value;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO for {@link cartservice.entities.CartItem}
 */
@Value
public class CartItemDto implements Serializable {
    Long id;

    Long cartId;

    @NotBlank(message = "Product SKU should be specified")
    String productSKU;

    @PositiveOrZero(message = "Unit Price cannot be negative")
    BigDecimal unitPrice;

    @Positive(message = "Quantity should be greater than zero")
    Long quantity;
}