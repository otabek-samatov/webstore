package paymentservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "refund", indexes = {
        @Index(name = "idx_refund_payment_id", columnList = "payment_id")
})
@SequenceGenerator(name = "entity_seq", sequenceName = "refund_seq", allocationSize = 50, initialValue = 1)
public class Refund extends CoreEntity {

    @OneToOne(cascade = CascadeType.PERSIST, optional = false)
    private Payment payment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

}