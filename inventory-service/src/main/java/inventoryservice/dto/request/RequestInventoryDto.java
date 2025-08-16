package inventoryservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.io.Serializable;

@Data
public class RequestInventoryDto implements Serializable {

    @NotBlank(message = "Product SKU should be specified")
    String productSKU;

    @NotNull(message = "Quantity should be specified")
    @PositiveOrZero(message = "Quantity cannot be negative")
    Long quantity;

}
