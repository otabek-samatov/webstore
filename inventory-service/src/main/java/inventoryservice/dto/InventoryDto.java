package inventoryservice.dto;

import inventoryservice.entities.MeasurementUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.io.Serializable;

/**
 * DTO for {@link inventoryservice.entities.Inventory}
 */
@Data
public class InventoryDto implements Serializable {
    Long id;

    @NotNull(message = "Stock Level should be specified")
    @PositiveOrZero(message = "Stock level cannot be negative")
    Long stockLevel;

    @NotNull(message = "Reserved Stock should be specified")
    @PositiveOrZero(message = "Reserved Stock cannot be negative")
    Long reservedStock;

    @NotBlank(message = "Product SKU should be specified")
    String productSKU;

    @NotNull(message = "Measurement Unit should be specified")
    MeasurementUnit measurementUnit;
}