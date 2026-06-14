package inventoryservice.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * Public API of the inbox. Consumer-side counterpart of {@code OutboxPublisher}.
 * <p>
 * The pattern: the consumer inserts a row keyed by a stable {@code messageId}
 * in the same DB transaction as its business side-effects. The unique PK
 * makes redelivered messages a structural no-op — exactly one transaction
 * can insert the row; redeliveries lose the race and skip the handler.
 * <p>
 * Producers of inbound events should stamp a message id (e.g. a Kafka header
 * such as {@code X-Message-Id}, or a stable business key like
 * {@code aggregateId-eventType-version}). Falling back to
 * {@code topic-partition-offset} is unsafe: a producer retry can land the
 * same logical event at a different offset.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InboxProcessor {

    private final InboxMessageRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * High-level entry point. Records the message and runs {@code handler}
     * exactly once, atomically with the recording. If the message has been
     * seen before, {@code handler} is skipped.
     * <p>
     * MUST be called inside an existing {@code @Transactional} boundary
     * (Propagation.MANDATORY) so the inbox row and business changes
     * commit together.
     *
     * @return {@code true} if the message was new and the handler ran;
     * {@code false} if it was a duplicate and was skipped.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean processOnce(InboxMessage message, Runnable handler) {
        if (!recordIfNew(message)) {
            log.debug("Duplicate inbox message ignored: messageId={}, topic={}",
                    message.getMessageId(), message.getTopicName());
            return false;
        }

        handler.run();

        repository.markProcessed(message.getMessageId(), Instant.now());

        log.debug("Inbox message processed: messageId={}, eventType={}",
                message.getMessageId(), message.getEventType());

        return true;
    }

    /**
     * Lower-level entry point: insert the inbox row if no row with that
     * messageId exists yet.
     * <p>
     * MUST be called inside an existing {@code @Transactional} boundary.
     *
     * @return {@code true} if a new row was inserted; {@code false} if a
     * row with the same messageId already exists (duplicate).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordIfNew(InboxMessage message) {
        // Fast path: a row already exists. Avoids hitting the DB constraint.
        if (repository.existsByMessageId(message.getMessageId())) {
            return false;
        }

        try {
            repository.save(message);
            return true;
        } catch (DataIntegrityViolationException raceLost) {
            // Concurrent insert from another consumer instance won the race.
            return false;
        }
    }

    /**
     * Convenience constructor for the common Kafka case. Serializes the
     * payload to JSON for storage.
     */
    public InboxMessage fromKafkaRecord(String messageId,
                                        String aggregateType,
                                        String aggregateId,
                                        String eventType,
                                        ConsumerRecord<?, ?> record,
                                        Object payload) {
        return new InboxMessage(
                messageId,
                aggregateType,
                aggregateId,
                eventType,
                record.topic(),
                record.partition(),
                record.offset(),
                serialize(payload)
        );
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "Failed to serialize inbox payload", e);
        }
    }
}
