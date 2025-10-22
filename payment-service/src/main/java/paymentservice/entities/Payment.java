package paymentservice.entities;

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
@Table(name = "payment", indexes = {
        @Index(name = "idx_payment_order_id", columnList = "order_id"),
        @Index(name = "idx_payment_user_id", columnList = "user_id")
})
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_seq")
    @SequenceGenerator(name = "payment_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull(message = "Order ID should be specified")
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @NotNull(message = "User ID should be specified")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @NotNull
    @PositiveOrZero(message = "Payment Amount should be non negative")
    @Column(name = "amount", nullable = false, precision = 9, scale = 2)
    private BigDecimal amount;

    @Version
    @Column(name = "version")
    private Integer version;

    @Setter(lombok.AccessLevel.NONE)
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Refund> refunds = new LinkedHashSet<>();


    public void addRefund(Refund refund) {
        refunds.add(refund);
        refund.setPayment(this);
    }

    public void removeRefund(Refund refund) {
        refunds.remove(refund);
        refund.setPayment(null);
    }

    public Set<Refund> getRefunds() {
        return Set.copyOf(refunds);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Payment payment = (Payment) o;
        return getId() != null && Objects.equals(getId(), payment.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}