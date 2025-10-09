package orderservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "order_item", uniqueConstraints = {
        @UniqueConstraint(name = "uc_orderitem_order_id", columnNames = {"order_id", "product_sku"})
})
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_item_seq")
    @SequenceGenerator(name = "order_item_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version")
    private Integer version;

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

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        OrderItem orderItem = (OrderItem) o;
        return getId() != null && Objects.equals(getId(), orderItem.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}