package entities;

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
        @Index(name = "idx_inventory_product_id", columnList = "product_id, product_class")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uc_inventory_product_id", columnNames = {"product_id", "product_class"})
})
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "inventory_seq")
    @SequenceGenerator(name = "inventory_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version")
    private Integer version;

    @NotNull(message = "Product Id should be specified")
    @Column(name = "product_id")
    private Long productId;

    @NotBlank(message = "Product class should be specified")
    @NotNull
    @Column(name = "product_class")
    private String productClass;

    @NotNull(message = "Stock Level should be specified")
    @PositiveOrZero(message = "Stock level cannot be negative")
    @Column(name = "stock_level", nullable = false)
    private BigDecimal stockLevel = BigDecimal.ZERO;

    @NotNull(message = "Reserved Stock should be specified")
    @PositiveOrZero(message = "Reserved Stock cannot be negative")
    @Column(name = "reserved_stock", nullable = false)
    private BigDecimal reservedStock = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_unit", nullable = false)
    private MeasurementUnit measurementUnit;

}