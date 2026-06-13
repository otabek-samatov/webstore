package inventoryservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "inventory", indexes = {
        @Index(name = "idx_inventory_product_sku_unq", columnList = "product_sku", unique = true)
})
@SequenceGenerator(name = "entity_seq", sequenceName = "inventory_seq", allocationSize = 50, initialValue = 1)
public class Inventory extends CoreEntity {

    @NotNull(message = "Stock Level should be specified")
    @PositiveOrZero(message = "Stock level cannot be negative")
    @Column(name = "stock_level", nullable = false)
    private Long stockLevel = 0L;

    @NotNull(message = "Reserved Stock should be specified")
    @PositiveOrZero(message = "Reserved Stock cannot be negative")
    @Column(name = "reserved_stock", nullable = false)
    private Long reservedStock = 0L;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_unit", nullable = false)
    private MeasurementUnit measurementUnit = MeasurementUnit.PIECE;

    @NotBlank(message = "Product SKU should be specified")
    @Column(name = "product_sku", nullable = false, unique = true)
    private String productSKU;

    @PositiveOrZero(message = "Stock Price cannot be negative")
    @Column(name = "stock_price", nullable = false, precision = 9, scale = 2)
    private BigDecimal stockPrice;

    @PositiveOrZero(message = "Sell Price cannot be negative")
    @Column(name = "sell_price", nullable = false, precision = 9, scale = 2)
    private BigDecimal sellPrice;

}