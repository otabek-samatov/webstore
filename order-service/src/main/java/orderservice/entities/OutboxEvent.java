package orderservice.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String aggregateType;   // e.g. "Order", "Product"

    @Column(nullable = false)
    private String aggregateId;     // e.g. the order ID

    @Column(nullable = false)
    private String eventType;       // e.g. "OrderCreated", "OrderCancelled"

    @Column(nullable = false)
    private String topicName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;         // JSON payload

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdAt;

    private Instant processedAt;

    public OutboxEvent(String aggregateType, String aggregateId,
                       String eventType, String topicName, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.topicName = topicName;
        this.eventType = eventType;
        this.payload = payload;
    }
}
