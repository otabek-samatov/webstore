package orderservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import orderservice.entities.Order;
import orderservice.entities.OrderStatus;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO for {@link Order}
 */
@Data
public class OrderDto implements Serializable {
    Long id;

    @NotNull(message = "Customer ID Should be specified")
    Long customerId;

    @PositiveOrZero(message = "Total Amount cannot be negative")
    BigDecimal totalAmount;

    @PositiveOrZero(message = "Tax Amount cannot be negative")
    BigDecimal taxAmount;

    @PositiveOrZero(message = "Shipping Cost cannot be negative")
    BigDecimal shippingCost;

    @NotNull(message = "Address Should be specified")
    AddressDto orderAddress;

    OrderStatus orderStatus;

}