package orderservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order extends CoreEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PositiveOrZero(message = "Total Amount cannot be negative")
    @Column(name = "total_amount", nullable = false, precision = 9, scale = 2)
    private BigDecimal totalAmount;

    @PositiveOrZero(message = "Tax Amount cannot be negative")
    @Column(name = "tax_amount", nullable = false, precision = 9, scale = 2)
    private BigDecimal taxAmount;

    @PositiveOrZero(message = "Shipping Cost cannot be negative")
    @Column(name = "shipping_cost", nullable = false, precision = 9, scale = 2)
    private BigDecimal shippingCost;

    @NotNull(message = "Address Should be specified")
    @Embedded
    private Address orderAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus = OrderStatus.NEW;

    @Setter(lombok.AccessLevel.NONE)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OrderItem> orderItems = new LinkedHashSet<>();

    public void addItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        orderItems.remove(item);
        item.setOrder(null);
    }

    public Set<OrderItem> getItems() {
        return Set.copyOf(orderItems);
    }

}