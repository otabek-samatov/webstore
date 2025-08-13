package inventoryservice.dto;

import inventoryservice.entities.MeasurementUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Value;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO for {@link inventoryservice.entities.Inventory}
 */
@Value
public class InventoryDto implements Serializable {
    Long id;

    @NotNull(message = "Stock Level should be specified")
    @PositiveOrZero(message = "Stock level cannot be negative")
    BigDecimal stockLevel;

    @NotNull(message = "Reserved Stock should be specified")
    @PositiveOrZero(message = "Reserved Stock cannot be negative")
    BigDecimal reservedStock;

    @NotBlank(message = "Produc SKU should be specified")
    String productSKU;

    @NotNull
    MeasurementUnit measurementUnit;
}