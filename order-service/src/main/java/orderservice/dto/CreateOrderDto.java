package orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderDto {

    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be a positive number")
    private Long customerId;

    @Valid
    @NotNull(message = "Order address is required")
    private AddressDto orderAddress;

    @Valid
    @NotEmpty(message = "Order must contain at least one item")
    private List<OrderItemDto> orderItems;
}