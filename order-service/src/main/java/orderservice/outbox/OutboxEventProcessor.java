package orderservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessor {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;


    // NOTE: no @Transactional here. Each repository.* call runs in its own
    // short Spring-Data-managed transaction (each @Modifying method on the
    // repository is annotated @Transactional). The claim's transaction
    // commits immediately, releasing the row lock before the Kafka send.
    public void processEvent(OutboxEvent event) {

        boolean claimed = repository.claimEvent(event.getId()) == 1;

        if (!claimed) {
            log.debug("Event {} already claimed by another instance", event.getId());
            return;
        }

        try {
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(event.getTopicName(), event.getAggregateId(), event.getPayload());

            SendResult<String, String> result = future.get();
            RecordMetadata metadata = result.getRecordMetadata();

            log.info("Event {} published: topic={}, partition={}, offset={}",
                    event.getId(),
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset());

            repository.markSent(event.getId(), Instant.now());

        } catch (Exception e) {
            log.error("Failed to publish event {}", event.getId(), e);
            repository.markPendingForRetry(event.getId());
        }
    }
}