package orderservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.entities.OutboxEvent;
import orderservice.repositories.OutboxEventRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessor {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public void processEvent(OutboxEvent event) {

        int claimed = repository.claimEvent(event.getId());

        if (claimed == 0) {
            log.debug("Event {} already claimed by another instance", event.getId());
            return;
        }

        try {
            String topic = resolveTopic(event);

            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());

            SendResult<String, String> result = future.get();

            RecordMetadata metadata = result.getRecordMetadata();

            log.info("Event {} published: topic={}, partition={}, offset={}",
                    event.getId(),
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset());

            repository.markSent(event.getId(), Instant.now());

        } catch (Exception e) {
            log.error("Failed to publish event {}: {}", event.getId(), e.getMessage());
            repository.markPendingForRetry(event.getId());
        }
    }

    private String resolveTopic(OutboxEvent event) {
        return event.getAggregateType().toLowerCase() + "-events";
    }
}
