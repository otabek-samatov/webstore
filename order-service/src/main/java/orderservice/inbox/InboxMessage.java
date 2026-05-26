package orderservice.inbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "inbox_messages")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxMessage {

    /**
     * The idempotency key supplied by the producer (e.g. a Kafka header value
     * or a stable business key such as {aggregateId}-{eventType}-{version}).
     * Used directly as the primary key so a duplicate insert raises
     * DataIntegrityViolationException — that is the linearization point.
     */
    @Id
    @Column(name = "message_id", length = 255, nullable = false)
    private String messageId;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(nullable = false)
    private String aggregateType;

    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String topicName;

    @Column(name = "partition_no")
    private Integer partition;

    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboxStatus status = InboxStatus.RECEIVED;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant receivedAt;

    private Instant processedAt;

    public InboxMessage(String messageId,
                        String aggregateType,
                        String aggregateId,
                        String eventType,
                        String topicName,
                        Integer partition,
                        Long kafkaOffset,
                        String payload) {
        this.messageId = messageId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topicName = topicName;
        this.partition = partition;
        this.kafkaOffset = kafkaOffset;
        this.payload = payload;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        InboxMessage that = (InboxMessage) o;
        return getMessageId() != null && Objects.equals(getMessageId(), that.getMessageId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
