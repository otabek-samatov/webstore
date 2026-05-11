package orderservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "order_item", uniqueConstraints = {
        @UniqueConstraint(name = "uc_orderitem_order_id", columnNames = {"order_id", "product_sku"})
})
public class OrderItem extends CoreEntity {

    @NotBlank(message = "Product SKU should be specified")
    @Column(name = "product_sku", nullable = false)
    private String productSKU;

    @Positive(message = "Unit Price  should be greater than zero")
    @Column(name = "unit_price", nullable = false, precision = 9, scale = 2)
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