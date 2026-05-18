package orderservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_customer_id", columnList = "customer_id")
})
@SequenceGenerator(name = "entity_seq", sequenceName = "orders_seq", allocationSize = 50, initialValue = 1)
public class Order extends CoreEntity {

    @NotNull
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PositiveOrZero(message = "Tax Amount cannot be negative")
    @Column(name = "tax_amount", nullable = false)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @PositiveOrZero(message = "Shipping Cost cannot be negative")
    @Column(name = "shipping_cost", nullable = false)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @NotNull(message = "Address Should be specified")
    @Embedded
    private Address orderAddress;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus = OrderStatus.NEW;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OrderItem> orderItems = new LinkedHashSet<>();

    public void addItem(OrderItem item) {
        if (orderStatus != OrderStatus.NEW) {
            throw new IllegalArgumentException(
                    "Cannot add items to order " + getId() + " in status " + orderStatus);
        }

        orderItems.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        if (orderStatus != OrderStatus.NEW) {
            throw new IllegalArgumentException(
                    "Cannot remove items from order " + getId() + " in status " + orderStatus);
        }

        orderItems.remove(item);
    }

    public Set<OrderItem> getItems() {
        return Set.copyOf(orderItems);
    }

    public void setOrderStatus(OrderStatus newOrderStatus) {
        if (this.orderStatus == newOrderStatus) {
            return;
        }

        if (!orderStatus.isAcceptableNextStatus(newOrderStatus)) {
            throw new IllegalArgumentException("order = " + getId() + " cannot be changed to " + newOrderStatus);
        }

        this.orderStatus = newOrderStatus;
    }
}