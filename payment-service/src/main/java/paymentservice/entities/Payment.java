package paymentservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_payment_order_id", columnList = "order_id"),
        @Index(name = "idx_payment_user_id", columnList = "user_id")
})
@SequenceGenerator(name = "entity_seq", sequenceName = "payment_seq", allocationSize = 50, initialValue = 1)
public class Payment extends CoreEntity {

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

}