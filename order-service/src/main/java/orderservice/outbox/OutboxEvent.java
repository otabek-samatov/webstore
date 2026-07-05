package orderservice.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
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

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;   // e.g. "Order", "Product"

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;     // e.g. the order ID

    @Column(name = "event_type", nullable = false)
    private String eventType;       // e.g. "OrderCreated", "OrderCancelled"

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;         // JSON payload

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public OutboxEvent(String aggregateType, String aggregateId,
                       String eventType, String topicName, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.topicName = topicName;
        this.eventType = eventType;
        this.payload = payload;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        OutboxEvent that = (OutboxEvent) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
