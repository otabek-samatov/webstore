package orderservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessor {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Qualifier("outboxClaimTxTemplate")
    private final TransactionTemplate claimTxTemplate;

    // NOTE: no @Transactional here. Each repository.* call below runs in its
    // own short Spring-Data-managed transaction. The claim runs in its own
    // tx via claimTxTemplate and commits before the Kafka send begins.
    public void processEvent(OutboxEvent event) {

        boolean claimed = Boolean.TRUE.equals(
                claimTxTemplate.execute(status ->
                        repository.claimEvent(event.getId()) == 1
                )
        );

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