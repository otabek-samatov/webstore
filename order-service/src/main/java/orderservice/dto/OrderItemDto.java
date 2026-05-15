package orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO for {@link orderservice.entities.OrderItem}
 */
@Data
public class OrderItemDto implements Serializable {
    Long id;

    @NotBlank(message = "Product SKU should be specified")
    String productSKU;

    @PositiveOrZero(message = "Unit Price should be specified")
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @NotNull
    @Positive(message = "Quantity should be greater than zero.")
    Long quantity;

    Long orderId;

    @NotBlank(message = "Product Name should be Specified")
    String productName;

}