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
            log.error("Failed to publish event {}: {}", event.getId(), e);
            repository.markPendingForRetry(event.getId());
        }
    }
}
