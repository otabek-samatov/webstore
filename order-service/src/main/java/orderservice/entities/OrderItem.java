package orderservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "order_item", indexes = {
        @Index(name = "idx_orderitem_order_id", columnList = "order_id"),
        @Index(name = "idx_orderitem_product_sku", columnList = "product_sku")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uc_orderitem_order_id", columnNames = {"order_id", "product_sku"})
})
@SequenceGenerator(name = "entity_seq", sequenceName = "order_item_seq", allocationSize = 50, initialValue = 1)
public class OrderItem extends CoreEntity {

    @NotBlank(message = "Product SKU should be specified")
    @Column(name = "product_sku", nullable = false)
    private String productSKU;

    @PositiveOrZero(message = "Unit Price should be specified")
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Positive(message = "Quantity should be greater than zero.")
    @Column(name = "quantity", nullable = false)
    private Long quantity = 1L;

    @NotNull(message = "Order should be specified")
    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private Order order;

    @NotBlank(message = "Product Name should be Specified")
    @Column(name = "product_name", nullable = false)
    private String productName;

}