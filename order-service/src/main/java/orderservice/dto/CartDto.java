package orderservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;


@Data
public class CartDto implements Serializable {
    Long id;

    @NotNull(message = "User ID should be specified")
    Long userId;

    LocalDateTime eventTime;
    @NotNull(message = "Status should be specified")
    orderservice.entities.CartStatus status;
    private Set<CartItemDto> cartItems = new LinkedHashSet<>();
}