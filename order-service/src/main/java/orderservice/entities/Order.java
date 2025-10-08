package orderservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orders_seq")
    @SequenceGenerator(name = "orders_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

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
    private OrderStatus orderStatus = OrderStatus.CREATED;

    @Setter(lombok.AccessLevel.NONE)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OrderItem> orderItems = new LinkedHashSet<>();

    public void addItem(OrderItem cartItem) {
        orderItems.add(cartItem);
        cartItem.setOrder(this);
    }

    public void removeItem(OrderItem cartItem) {
        orderItems.remove(cartItem);
        cartItem.setOrder(null);
    }

    public Set<OrderItem> getItems() {
        return Set.copyOf(orderItems);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Order order = (Order) o;
        return getId() != null && Objects.equals(getId(), order.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}