package paymentservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "refund", indexes = {
        @Index(name = "idx_refund_payment_id", columnList = "payment_id")
})
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "refund_seq")
    @SequenceGenerator(name = "refund_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;


    @Positive(message = "Refund Amount should be greater than zero")
    @Column(name = "refund_amount", nullable = false, precision = 9, scale = 2)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false)
    private RefundStatus refundStatus;

    @Version
    @Column(name = "version")
    private Integer version;

    @ManyToOne(cascade = CascadeType.PERSIST, optional = false)
    private Payment payment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Refund refund = (Refund) o;
        return getId() != null && Objects.equals(getId(), refund.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}