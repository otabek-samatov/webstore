package productservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public API of the outbox starter.
 * Business services call this within their existing transaction
 * to record an event in the outbox table.
 * Usage:
 * outboxPublisher.publish("Order", order.getId(), "OrderCreated", orderDto);
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    @Value("${topic.stock.status}")
    private String stockStatusTopic;

    /**
     * Records an event in the outbox table.
     * MUST be called within an existing transaction (Propagation.MANDATORY)
     * so it participates in the caller's transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, String aggregateId,
                        String eventType, String topicName, Object payload) {

        String json = serialize(payload);

        OutboxEvent event = new OutboxEvent(
                aggregateType,
                aggregateId,
                eventType,
                topicName,
                json
        );

        repository.save(event);

        log.debug("Outbox event recorded: type={}, aggregateId={}",
                eventType, aggregateId);
    }


    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to serialize outbox payload", e);
        }
    }
}
